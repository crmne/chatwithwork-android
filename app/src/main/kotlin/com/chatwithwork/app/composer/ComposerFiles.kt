package com.chatwithwork.app.composer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.chatwithwork.app.routing.AppUrls
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads files the person picked, photographed, scanned, or shared into the
 * `attach` payload: name, media type, and base64 bytes. Files over
 * [LIMIT_BYTES] are left out and named, since they'd travel through the
 * bridge as text; the web composer applies its own limits after that.
 */
object ComposerFiles {
    const val LIMIT_BYTES = 25L * 1024 * 1024

    class Read(val files: List<ComposerFile>, val tooBig: List<String>, val unreadable: Boolean)

    suspend fun read(context: Context, uris: List<Uri>): Read = withContext(Dispatchers.IO) {
        val files = mutableListOf<ComposerFile>()
        val tooBig = mutableListOf<String>()
        var unreadable = false
        uris.forEach { uri ->
            val (name, size) = describe(context, uri)
            if (size != null && size > LIMIT_BYTES) {
                tooBig += name
                return@forEach
            }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                when {
                    bytes == null -> unreadable = true

                    bytes.size > LIMIT_BYTES -> tooBig += name

                    else ->
                        files +=
                            ComposerFile(
                                name = name,
                                type = context.contentResolver.getType(uri) ?: typeFor(name),
                                data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            )
                }
            } catch (_: IOException) {
                unreadable = true
            } catch (_: SecurityException) {
                unreadable = true
            }
        }
        Read(files, tooBig, unreadable)
    }

    private fun describe(context: Context, uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                            name =
                                cursor.getString(it)
                        }
                        cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let {
                            size = cursor.getLong(it)
                        }
                    }
                }
        }
        return (name ?: uri.lastPathSegment ?: "file") to size
    }

    /** The media type for a name, when the provider gives none. */
    fun typeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/**
 * What waits for the next composer that connects: files shared in from
 * another app (into a new chat), dictation asked for from the home screen
 * widget, and the field's focus asked for by invoking the assistant. Kept in
 * the process; an intent can only ask for the focus, which sends nothing.
 */
object PendingShares {
    private var files: List<ComposerFile>? = null
    private var dictation = false
    private var focus = false

    fun putFiles(files: List<ComposerFile>) {
        this.files = files
    }

    fun takeFiles(): List<ComposerFile>? = files.also { files = null }

    fun askDictation() {
        dictation = true
    }

    fun takeDictation(): Boolean = dictation.also { dictation = false }

    fun askFocus() {
        focus = true
    }

    /** The focus is for the new chat the assistant opened, not a chat on screen that connects first as its answer ends. */
    fun takeFocus(location: String): Boolean = AppUrls.isNewChat(location) && focus.also { focus = false }
}
