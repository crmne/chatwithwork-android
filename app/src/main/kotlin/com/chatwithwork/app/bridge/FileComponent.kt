package com.chatwithwork.app.bridge

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.R
import com.chatwithwork.app.files.FileDownloads
import com.chatwithwork.app.routing.AppUrls
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.fragments.HotwireWebFragment
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * `file`: Share in the top app bar of a file's viewer (the server's
 * `chats/files/show`), for the file itself rather than a link to it, and
 * "Save to device" in the overflow menu.
 *
 * - `connect` with `{files: [{url, name, contentType?, title?}]}`, the file
 *   first, then the PDF of its pages once the server has made one. With more
 *   than one, each action first asks which, by `title`, which says what the
 *   file is ("PowerPoint", "PDF").
 * - `disconnect` removes them.
 *
 * Both download the file with the web view's session (FileDownloads), only
 * from this app's own server: Share hands it to Android's share sheet through
 * the FileProvider, and Save writes it where the person picks. Nothing goes
 * back to the page. The same contract as the iOS app's.
 */
class FileComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_FILE

    private var files: List<SharedFile> = emptyList()
    private var busy = false

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                files = message.data<Data>()?.files.orEmpty()
                redraw()
            }

            "disconnect" -> {
                files = emptyList()
                clear()
            }
        }
    }

    override fun render(toolbar: Toolbar) {
        if (files.isEmpty()) return
        val context = toolbar.context
        val share = context.getString(R.string.share)

        toolbar.menu.add(menuGroup, ITEM_SHARE, ORDER_FILE, share).apply {
            icon = SymbolDrawable(context, "share")
            tint(iconColor(toolbar, null))
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            describe(share)
            isEnabled = !busy
            setOnMenuItemClickListener {
                choose(toolbar, share) { share(it) }
                true
            }
        }
        toolbar.menu.add(menuGroup, ITEM_SAVE, ORDER_FILE + 1, R.string.file_save).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isEnabled = !busy
            setOnMenuItemClickListener {
                choose(toolbar, context.getString(R.string.file_save)) { save(it) }
                true
            }
        }
    }

    /** Runs [then] with the one file, or the one the person picks from several under [action]. */
    private fun choose(toolbar: Toolbar, action: String, then: (SharedFile) -> Unit) {
        val shown = files
        if (shown.size == 1) return then(shown.first())

        MaterialAlertDialogBuilder(toolbar.context)
            .setTitle(action)
            .setItems(shown.map { it.title ?: it.name }.toTypedArray()) { _, which -> then(shown[which]) }
            .show()
    }

    private fun share(file: SharedFile) {
        download(file) { local ->
            val activity = destination.fragment.activity ?: return@download
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", local)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = file.contentType ?: "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            activity.startActivity(Intent.createChooser(send, null))
        }
    }

    private fun save(file: SharedFile) {
        val activity = destination.fragment.activity ?: return
        val host = activity as? FileSaveHost ?: return
        host.chooseSaveLocation(file.name, file.contentType ?: "application/octet-stream") { target ->
            if (target == null) return@chooseSaveLocation
            download(file) { local ->
                val saved = withContext(Dispatchers.IO) { copy(local, target) }
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(if (saved) R.string.file_saved else R.string.file_save_failed),
                    long = !saved
                )
            }
        }
    }

    private fun copy(local: File, target: Uri): Boolean {
        val activity = destination.fragment.activity ?: return false
        return try {
            activity.contentResolver.openOutputStream(target)?.use { out ->
                local.inputStream().use { it.copyTo(out) }
            } !=
                null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Downloads [file], greying the actions meanwhile, then runs [then] with it. */
    private fun download(file: SharedFile, then: suspend (File) -> Unit) {
        val activity = destination.fragment.activity ?: return
        val url = file.resolvedUrl(page = webView()?.url) ?: return
        if (busy || !AppUrls.isAppUrl(url)) return

        busy = true
        redraw()
        activity.lifecycleScope.launch {
            val local = FileDownloads.fetch(activity, url, file.name, webView()?.settings?.userAgentString)
            busy = false
            redraw()
            if (local == null) {
                (activity as? SnackbarHost)?.showSnackbar(
                    activity.getString(R.string.file_download_failed),
                    long = true
                )
            } else {
                then(local)
            }
        }
    }

    private fun webView() = (destination.fragment as? HotwireWebFragment)?.navigator?.session?.webView

    @Serializable
    data class Data(val files: List<SharedFile> = emptyList())

    @Serializable
    data class SharedFile(
        val url: String,
        val name: String,
        val contentType: String? = null,
        val title: String? = null
    ) {
        /** The file's address, relative to [page] when the page gave a path; null for anything but http(s). */
        fun resolvedUrl(page: String?): String? {
            val resolved =
                runCatching {
                    if (page != null) java.net.URI(page).resolve(url) else java.net.URI(url)
                }.getOrNull() ?: return null
            val scheme = resolved.scheme?.lowercase()
            return resolved.toString().takeIf { scheme == "https" || scheme == "http" }
        }
    }

    companion object {
        private const val ITEM_SHARE = 0x0C600
        private const val ITEM_SAVE = 0x0C601

        val factory = BridgeComponentFactory("file", ::FileComponent)
    }
}

/** Implemented by the activity, which asks the system where to save a file. */
interface FileSaveHost {
    /** Asks the person where to save a file called [name] of [type]; hands back the place, or null. */
    fun chooseSaveLocation(name: String, type: String, onResult: (Uri?) -> Unit)
}
