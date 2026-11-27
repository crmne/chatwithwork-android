package com.chatwithwork.app.composer

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputContentInfo
import androidx.core.os.BundleCompat
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat

/**
 * Pictures and files pasted, inserted by the keyboard, or dropped on the
 * composer: they go to it as attachments, the same way as files the person
 * picks, and words still go in as words.
 *
 * Everything arrives through Android's one way of receiving rich content
 * ([OnReceiveContentListener], `ViewCompat.setOnReceiveContentListener`):
 * the keyboard's pictures (Gboard's clipboard and its suggestion to paste
 * what was just copied, GIFs, stickers, image keyboards), a paste into the
 * native composer's field, and a drop from another app on a tablet or in
 * split screen. What it decides is [files], the same rule as the web
 * composer's paste (`chat#onPaste`, `filesAreAPictureOfText`).
 */
object PastedContent {
    /** What the composer says the keyboard may insert: pictures, GIFs and stickers. */
    val KEYBOARD_TYPES = arrayOf("image/*")

    /** One item of a clip, as the rule needs it. */
    data class Item(val text: CharSequence? = null, val html: String? = null, val uri: Uri? = null)

    /**
     * The files a clip holds for the composer, or none when it's words (or
     * empty). As on the web:
     *
     * - no file in it: words (a copied link is words, not a file);
     * - files and no words: the files (a screenshot, Chrome's Copy image, a
     *   picture from the keyboard);
     * - files and words: the files, unless the HTML beside them has words of
     *   its own, which is a range or a table copied from a spreadsheet or a
     *   document with a picture of it beside: that's its words. Words without
     *   HTML are a copied file's name.
     */
    fun files(items: List<Item>): List<Uri> {
        val files = items.mapNotNull { it.uri?.takeIf(::isFile) }
        if (files.isEmpty()) return emptyList()
        val text = items.mapNotNull { it.text?.toString() }.joinToString("")
        if (text.isBlank()) return files
        return if (items.any { hasWords(it.html) }) emptyList() else files
    }

    fun files(clip: ClipData?): List<Uri> = files(items(clip))

    fun items(clip: ClipData?): List<Item> = clip?.let {
        (0 until it.itemCount).map { index ->
            val item = it.getItemAt(index)
            Item(item.text, item.htmlText, item.uri)
        }
    }.orEmpty()

    /**
     * Whether a clip with this description may hold a file, for a drag to
     * be taken before its contents can be read: anything that isn't only
     * words or links.
     */
    fun mayHoldFiles(description: ClipDescription?): Boolean {
        description ?: return false
        return (0 until description.mimeTypeCount).any { description.getMimeType(it) !in WORDS }
    }

    /** Content (or file) addresses are files; a web link is words. */
    fun isFile(uri: Uri): Boolean = uri.scheme in setOf("content", "file")

    /** The HTML's own words, as the web reads `textContent`: tags, comments, entities and spacing aside. */
    fun hasWords(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        val text =
            html
                .replace(Regex("(?is)<(script|style|head)\\b.*?</\\1>"), " ")
                .replace(Regex("(?s)<!--.*?-->"), " ")
                .replace(Regex("(?s)<[^>]*>"), " ")
                .replace(Regex("&(nbsp|#160|#xa0);", RegexOption.IGNORE_CASE), " ")
        return text.isNotBlank()
    }

    private val WORDS =
        setOf(
            ClipDescription.MIMETYPE_TEXT_PLAIN,
            ClipDescription.MIMETYPE_TEXT_HTML,
            ClipDescription.MIMETYPE_TEXT_URILIST,
            ClipDescription.MIMETYPE_TEXT_INTENT
        )

    /**
     * The screen that takes what's received: MainActivity, which hands the
     * files to the composer on screen.
     */
    interface Host {
        /** Whether the screen showing has a composer that takes files now. */
        fun composerTakesFiles(): Boolean

        /** Files received, readable until [release]; on the main thread. */
        fun receivePasted(uris: List<Uri>, release: () -> Unit)
    }

    fun hostOf(context: Context): Host? {
        var current: Context? = context
        while (current != null) {
            if (current is Host) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    /**
     * The web view's receiver: files go to the [Host], everything else stays
     * where it came from (the page pastes words itself). The keyboard calls
     * it on its own thread, so the host hears on the main thread, and the
     * keyboard's grant to read the picture lasts until the host has read it.
     */
    val receiver =
        OnReceiveContentListener { view, payload ->
            val files = files(payload.clip)
            val host = hostOf(view.context)
            if (files.isEmpty() || host == null) return@OnReceiveContentListener payload
            val grant = keyboardGrant(payload)
            val release = {
                grant?.releasePermission()
                Unit
            }
            main.post { host.receivePasted(files, release) }
            null
        }

    private fun keyboardGrant(payload: ContentInfoCompat): InputContentInfo? {
        if (payload.source != ContentInfoCompat.SOURCE_INPUT_METHOD ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1
        ) {
            return null
        }
        // InputConnectionCompat puts the keyboard's InputContentInfo in the extras, under a key of its own it keeps
        // private; it's the one InputContentInfo there.
        val extras = payload.extras ?: return null
        return extras.keySet().firstNotNullOfOrNull { key ->
            runCatching { BundleCompat.getParcelable(extras, key, InputContentInfo::class.java) }.getOrNull()
        }
    }

    private val main by lazy { Handler(Looper.getMainLooper()) }
}
