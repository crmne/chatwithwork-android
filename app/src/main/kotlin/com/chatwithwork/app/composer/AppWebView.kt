package com.chatwithwork.app.composer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.DragEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import dev.hotwire.core.turbo.webview.HotwireWebView

/**
 * The app's web view, which takes pictures and files into the page's
 * composer the way Android hands them over (PastedContent):
 *
 * - **The keyboard's pictures.** Gboard's clipboard, its suggestion to paste
 *   what was just copied, GIFs, stickers and image keyboards insert a
 *   picture as rich content (`commitContent`), never as a paste, and
 *   Chromium's web view takes none: the keyboard said the app doesn't
 *   support images here. While the screen's composer takes files, the field
 *   says it takes pictures, and one inserted goes to the composer.
 * - **A drop** from another app (tablets, split screen) holding files,
 *   which Chromium would open or ignore rather than give the page.
 *
 * Long-press Paste needs none of this: Chromium offers Paste for a picture
 * and gives the page an ordinary paste with it, which the page attaches, or
 * pastes as words when the clip has words of its own (`chat#onPaste`).
 */
class AppWebView(context: Context) : HotwireWebView(context) {
    init {
        ViewCompat.setOnReceiveContentListener(this, PastedContent.KEYBOARD_TYPES, PastedContent.receiver)
    }

    private val host: PastedContent.Host? get() = PastedContent.hostOf(context)

    /** Whether the keyboard was last told the field takes pictures. */
    private var offeredPictures = false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        offeredPictures = connection != null && host?.composerTakesFiles() == true
        if (!offeredPictures || connection == null) return connection
        EditorInfoCompat.setContentMimeTypes(outAttrs, PastedContent.KEYBOARD_TYPES)
        return InputConnectionCompat.createWrapper(this, connection, outAttrs)
    }

    /**
     * The composer on screen came or went, or started or stopped taking
     * files: a keyboard already up for the page hears again whether the
     * field takes pictures (New chat focuses its composer as it loads,
     * sometimes before its composer has said anything).
     */
    fun composerChanged() {
        if (!hasFocus() || offeredPictures == (host?.composerTakesFiles() == true)) return
        val keyboard = context.getSystemService<InputMethodManager>() ?: return
        if (keyboard.isActive(this)) keyboard.restartInput(this)
    }

    /** A drag that may hold files, taken by the app rather than the page while the composer takes files. */
    private var draggingFiles = false

    override fun onDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // A text file can say only text/plain: whether it's files or words is decided at the drop (files).
                draggingFiles = event.clipDescription != null && host?.composerTakesFiles() == true
                return super.onDragEvent(event) || draggingFiles
            }

            DragEvent.ACTION_DROP -> if (draggingFiles && dropFiles(event)) return true

            DragEvent.ACTION_DRAG_ENDED -> draggingFiles = false
        }
        return super.onDragEvent(event)
    }

    /** A drop's files go to the composer, read under the drop's grant; a drop of words is the page's. */
    private fun dropFiles(event: DragEvent): Boolean {
        val clip = event.clipData ?: return false
        val files = PastedContent.files(clip)
        val host = host ?: return false
        if (files.isEmpty()) return false
        val grant = activity()?.requestDragAndDropPermissions(event)
        host.receivePasted(files) { grant?.release() }
        return true
    }

    private fun activity(): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }
}
