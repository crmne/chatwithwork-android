package com.chatwithwork.app.bridge

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.chatwithwork.app.R
import com.chatwithwork.app.composer.ComposerFile
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.fragments.WebFragment
import com.chatwithwork.app.fragments.WebScreenHost
import com.chatwithwork.app.push.LiveRunRef
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * `web-composer`: the page's own composer, which the builds that draw none of
 * their own show (AppConfig.NATIVE_COMPOSER off). The page draws and sends
 * everything; the app only keeps what it needs from it (docs/server-contract.md,
 * "The web composer"):
 *
 * - Web to app: `connect`, then `state` whenever it changes, each with
 *   [WebComposerState]. The screen keeps the latest ([WebFragment.webComposer]),
 *   so a Live Update can follow an answer left running.
 * - App to web, as replies to the latest `connect`: `attach {files}` (files
 *   shared into New chat), `dictate` (the widget's or a shortcut's Dictate,
 *   which clicks the page's microphone once Android allows the microphone),
 *   and `closeMenus` (Back while one of the composer's menus is open, `menu`).
 */
class WebComposerComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val fragment get() = bridgeDelegate.destination.fragment
    private val activity: FragmentActivity? get() = fragment.activity

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect", "state" -> {
                val state = message.data<WebComposerState>() ?: return
                (fragment as? WebFragment)?.keepWebComposer(
                    state,
                    message.metadata?.url ?: bridgeDelegate.destination.location
                )
                takeBack(state.menu)
                if (message.event == "connect") onConnected(state)
                // Dictation asked for from outside also starts once the composer can take it (the yes to models).
                if (AppUrls.isNewChat(bridgeDelegate.destination.location) && state.dictates &&
                    PendingShares.takeDictation()
                ) {
                    dictate()
                }
            }
        }
    }

    /** What waits outside the page for New chat's composer: files shared in, and dictation asked for. */
    private fun onConnected(state: WebComposerState) {
        if (!AppUrls.isNewChat(bridgeDelegate.destination.location)) return
        if (state.acceptsFiles) PendingShares.takeFiles()?.let(::attach)
    }

    private var back: OnBackPressedCallback? = null
    private var backOwner: LifecycleOwner? = null

    /**
     * While one of the composer's menus is open, Back closes it, as Back
     * closes a menu anywhere on Android, rather than going back or leaving
     * the app with the menu over the page. Added once a menu first opens on
     * this screen's view, after the navigator's own, so it comes first.
     */
    private fun takeBack(menuOpen: Boolean) {
        val activity = activity ?: return
        if (fragment.view == null) return
        val owner = fragment.viewLifecycleOwner
        if (back == null || backOwner !== owner) {
            if (!menuOpen) return
            back?.remove()
            back =
                object : OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() {
                        isEnabled = false
                        // The sidebar slid open over the page takes Back itself, and the menu waits.
                        if ((activity as? WebScreenHost)?.coversPage == true) {
                            activity.onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                            return
                        }
                        replyTo("connect", closeMenusReply().toString())
                    }
                }.also { activity.onBackPressedDispatcher.addCallback(owner, it) }
            backOwner = owner
        }
        back?.isEnabled = menuOpen
    }

    private fun attach(files: List<ComposerFile>) {
        if (files.isNotEmpty()) replyTo("connect", attachReply(files).toString())
    }

    /**
     * Android's microphone prompt first, where it's still to answer, so the
     * page's click starts recording at once rather than behind a prompt that
     * its getUserMedia would raise anyway.
     */
    private fun dictate() {
        val activity = activity ?: return
        val host = activity as? ComposerHost ?: return
        host.requestMicrophone { granted ->
            if (granted) {
                replyTo("connect", dictateReply().toString())
            } else {
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(R.string.dictation_needs_microphone),
                    long = true
                )
            }
        }
    }

    companion object {
        const val NAME = "web-composer"

        val factory = BridgeComponentFactory(NAME, ::WebComposerComponent)

        internal fun attachReply(files: List<ComposerFile>): JsonObject = buildJsonObject {
            put("action", "attach")
            put("files", Json.encodeToJsonElement(files))
        }

        internal fun dictateReply(): JsonObject = buildJsonObject { put("action", "dictate") }

        internal fun closeMenusReply(): JsonObject = buildJsonObject { put("action", "closeMenus") }
    }
}

/**
 * The web composer's state, as `bridge--web-composer` sends it. `working`,
 * `waiting`, `run`, `revision`, `startedAt` (unix seconds) and `mark` are the
 * native composer's own ([com.chatwithwork.app.composer.ComposerState]).
 */
@Serializable
data class WebComposerState(
    val working: Boolean = false,
    val waiting: Boolean = false,
    val run: String? = null,
    val revision: Long? = null,
    val startedAt: Long? = null,
    val mark: String? = null,
    val csrfToken: String? = null,
    val acceptsFiles: Boolean = false,
    val dictates: Boolean = false,
    /** One of the composer's menus is open (+, the model, the mode, the slash menu); absent from older pages. */
    val menu: Boolean = false
) {
    val liveRun: LiveRunRef? get() = run?.takeIf { it.isNotBlank() }?.let {
        LiveRunRef(it, startedAt?.let { seconds -> seconds * 1000 }, revision ?: 0)
    }
}
