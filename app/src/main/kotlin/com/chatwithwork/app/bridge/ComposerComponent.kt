package com.chatwithwork.app.bridge

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.R
import com.chatwithwork.app.composer.AttachSheet
import com.chatwithwork.app.composer.ComposerBar
import com.chatwithwork.app.composer.ComposerFile
import com.chatwithwork.app.composer.ComposerFiles
import com.chatwithwork.app.composer.ComposerState
import com.chatwithwork.app.composer.ComposerView
import com.chatwithwork.app.composer.ModelLogo
import com.chatwithwork.app.composer.NewChatDrafts
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * `composer`: the app draws the composer itself and drives the web one,
 * which stays in the page out of sight (native.css), so every rule it carries
 * holds: locks, who may steer, the queue, files waiting for their scan, the
 * model, and web search. See docs/server-contract.md, "The composer".
 *
 * - Web to native: `connect` and then `state`, each with the composer's
 *   state ([ComposerState]). A chat draws its composer again as an answer
 *   starts and ends; each new one connects again, and replies go to the
 *   latest `connect`.
 * - Native to web, as replies to `connect`, `{action, ...}`: `send {text}`,
 *   `stop`, `model {index}`, `webSearch {on}`, `permissionMode {value}`,
 *   `narrow` (opens the web's Narrow services), `attach {files}`,
 *   `remove {id}`, and `text {text}` (dictated words).
 */
class ComposerComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate),
    ComposerView.Actions {
    private val fragment get() = bridgeDelegate.destination.fragment
    private val view: ComposerView? get() = fragment.view?.findViewById(R.id.composer)
    private val activity: FragmentActivity? get() = fragment.activity

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect", "state" -> {
                val state = message.data<ComposerState>() ?: return
                val view = view ?: return
                view.actions = this
                view.show(state, connecting = message.event == "connect")
                if (message.event == "connect") onConnected(state)
            }
        }
    }

    /**
     * New chat's draft (NewChatDrafts), files shared into a new chat,
     * dictation from the widget, and the assistant's focus wait for the
     * composer that takes them.
     */
    private fun onConnected(state: ComposerState) {
        if (AppUrls.isNewChat(location)) keepDraft(filled = state.fill)
        if (state.acceptsFiles) PendingShares.takeFiles()?.let(::attachFiles)
        if (PendingShares.takeDictation() && state.dictation != null) dictate()
        if (PendingShares.takeFocus(bridgeDelegate.destination.location)) view?.focusField()
    }

    override fun send(text: String) {
        NewChatDrafts.clear(location)
        reply("send") { put("text", text) }
    }

    private val location: String get() = bridgeDelegate.destination.location

    private var keepingDraft = false

    /**
     * Puts New chat's draft back in the field, joined with any words a share
     * put there (not when a pressed suggestion [filled] it), and keeps what's in the field as the draft when the screen
     * goes (closed, a rotation, or the chat sent, which leaves it empty).
     */
    private fun keepDraft(filled: Boolean) {
        val view = view ?: return
        val draft = NewChatDrafts.take(location)
        // A pressed suggestion's words replace the field, the draft with it.
        if (!filled) NewChatDrafts.joined(draft, view.text)?.let(view::replaceText)
        if (keepingDraft) return
        keepingDraft = true
        val location = location
        val session = NewChatDrafts.session
        fragment.viewLifecycleOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    this.view?.text?.let { NewChatDrafts.keep(location, it, typedIn = session) }
                    keepingDraft = false
                }
            }
        )
    }

    override fun stop() = reply("stop")

    override fun remove(id: String) = reply("remove") { put("id", id) }

    override fun attachPasted(uris: List<Uri>) = attachUris(uris)

    override fun layout(height: Int, width: Int) {
        replyTo("connect", layoutReply(height, width).toString())
    }

    override fun chooseModel() {
        val context = fragment.context ?: return
        val models = view?.state?.models.orEmpty()
        if (models.isEmpty()) return
        ActionSheet.show(
            context,
            context.getString(R.string.composer_models_title),
            models.map { model ->
                ActionSheet.Action(
                    title = model.name,
                    subtitle = model.rate,
                    icon = ModelLogo.drawable(context, model),
                    checked = model.selected
                )
            }
        ) { index -> reply("model") { put("index", index) } }
    }

    override fun openSheet() {
        showSheet(view?.sheet ?: return)
    }

    override fun openChanges() = showSheet(ComposerBar.Sheet.CHANGES)

    private fun showSheet(sheet: ComposerBar.Sheet) {
        val activity = activity ?: return
        val view = view ?: return
        val host = activity as? ComposerHost ?: return
        AttachSheet.show(
            activity,
            view.state,
            sheet,
            object : AttachSheet.Choices {
                override fun camera() = host.takePhoto { it?.let { uri -> attachUris(listOf(uri)) } }

                override fun photos() = host.pickPhotos(::attachUris)

                override fun files() = host.pickFiles(::attachUris)

                override fun scan() = host.scanDocument { it?.let { uri -> attachUris(listOf(uri)) } }

                override fun dictate() = this@ComposerComponent.dictate()

                override fun webSearch(on: Boolean) = this@ComposerComponent.webSearch(on)

                override fun narrow() {
                    replyTo("connect", narrowReply().toString())
                }

                override fun permissionMode(value: String) {
                    if (this@ComposerComponent.view?.state?.canSelectPermissionMode(value) == true) {
                        reply("permissionMode") { put("value", value) }
                    }
                }
            }
        )
    }

    override fun webSearch(on: Boolean) = reply("webSearch") { put("on", on) }

    override fun explainWebSearch(note: String?) {
        if (!note.isNullOrBlank()) (activity as? SnackbarHost)?.showSnackbar(note, long = true)
    }

    override fun dictate() {
        val activity = activity ?: return
        val url = view?.state?.dictation?.url ?: return
        val host = activity as? ComposerHost ?: return
        host.requestMicrophone { granted ->
            if (!granted) {
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(R.string.dictation_needs_microphone),
                    long = true
                )
                return@requestMicrophone
            }
            val view = view ?: return@requestMicrophone
            val started =
                view.dictate(url, view.state.csrfToken, userAgent()) { result ->
                    val words = result.text
                    when {
                        words != null -> {
                            this.view?.append(words)
                            reply("text") { put("text", words) }
                        }

                        result.nothingHeard -> snackbar(activity.getString(R.string.dictation_no_speech))

                        else -> snackbar(result.error ?: activity.getString(R.string.dictation_failed))
                    }
                }
            if (!started) snackbar(activity.getString(R.string.dictation_failed))
        }
    }

    private fun snackbar(message: String) {
        (activity as? SnackbarHost)?.showSnackbar(message, long = true)
    }

    private fun userAgent(): String? = (fragment as? dev.hotwire.navigation.fragments.HotwireWebFragment)
        ?.navigator
        ?.session
        ?.webView
        ?.settings
        ?.userAgentString

    private fun attachUris(uris: List<Uri>) {
        val activity = activity ?: return
        if (uris.isEmpty()) return
        activity.lifecycleScope.launch {
            val read = ComposerFiles.read(activity, uris)
            read.tooBig.forEach { name ->
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(R.string.composer_file_too_big, name),
                    long = true
                )
            }
            if (read.unreadable) {
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(R.string.composer_file_unreadable),
                    long = true
                )
            }
            attachFiles(read.files)
        }
    }

    private fun attachFiles(files: List<ComposerFile>) {
        if (files.isEmpty()) return
        reply("attach") { put("files", Json.encodeToJsonElement(files)) }
    }

    private fun reply(action: String, fill: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) {
        replyTo("connect", actionReply(action, fill).toString())
    }

    companion object {
        const val NAME = "composer"

        val factory = BridgeComponentFactory(NAME, ::ComposerComponent)

        internal fun actionReply(
            action: String,
            fill: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}
        ): JsonObject = buildJsonObject {
            put("action", JsonPrimitive(action))
            fill()
        }

        /** Opens the web's Narrow services dialog; the page then sends `visible: false` until it closes. */
        internal fun narrowReply(): JsonObject = actionReply("narrow")

        internal fun layoutReply(height: Int, width: Int): JsonObject = buildJsonObject {
            put("action", "layout")
            put("height", height)
            put("width", width)
        }
    }
}

/** Implemented by the activity, which owns the system pickers, the camera, the scanner, and the microphone prompt. */
interface ComposerHost {
    fun takePhoto(onResult: (Uri?) -> Unit)

    fun pickPhotos(onResult: (List<Uri>) -> Unit)

    fun pickFiles(onResult: (List<Uri>) -> Unit)

    fun scanDocument(onResult: (Uri?) -> Unit)

    fun requestMicrophone(onResult: (granted: Boolean) -> Unit)
}
