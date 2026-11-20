package com.chatwithwork.app.composer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.chatwithwork.app.routing.AppUrls
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

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

    /** Files a composer was handed and couldn't take, ahead of any shared since. */
    fun putBack(taken: List<ComposerFile>) {
        if (taken.isNotEmpty()) files = taken + files.orEmpty()
    }

    /**
     * Files handed to a page that answers for them (`confirmsAttach`), by the
     * id their `attach` carried, until its `attached` says what it took
     * ([settle]). The page answers every attach while it lives, so only a
     * page gone away leaves them unanswered: [returnUnanswered] puts them back.
     */
    private class InFlight(val files: List<ComposerFile>, val sentAt: Long)

    private val inFlight = LinkedHashMap<String, InFlight>()

    /** Files given back before their answer came, so a late answer can still take back what the page took. */
    private val returned = LinkedHashMap<String, List<ComposerFile>>()

    /** How long files wait for a page's answer: long enough for a large upload on a slow network. */
    const val UNANSWERED_MS = 10 * 60 * 1000L

    /** The files that wait, now in flight under a new id, for a page that answers for them. */
    fun sendFiles(now: Long = System.currentTimeMillis()): Pair<String, List<ComposerFile>>? {
        val sent = takeFiles() ?: return null
        val id = UUID.randomUUID().toString()
        inFlight[id] = InFlight(sent, now)
        return id to sent
    }

    /**
     * The page's answer for the attach [id]: each file it took, or let go
     * (`refused`, `removed`), leaves; any other file waits again for the next
     * New chat. An answer for files already given back takes the same files
     * out of those waiting.
     */
    fun settle(id: String?, results: List<AttachedFile>) {
        val sent = id?.let { inFlight.remove(it) }
        if (sent != null) {
            putBack(sent.files.filterIndexed { index, _ -> keeps(results.getOrNull(index)) })
            return
        }
        val given = id?.let { returned.remove(it) } ?: return
        val gone = given.filterIndexed { index, _ -> !keeps(results.getOrNull(index)) }
        var waiting = files ?: return
        for (file in gone) {
            val at = waiting.indexOf(file)
            if (at >= 0) waiting = waiting.filterIndexed { index, _ -> index != at }
        }
        files = waiting.ifEmpty { null }
    }

    /**
     * Gives back files in flight that no answer can come for: all of them
     * (the page went away), or those sent before [olderThan].
     */
    fun returnUnanswered(olderThan: Long? = null) {
        val stale = inFlight.filterValues { olderThan == null || it.sentAt < olderThan }
        for ((id, flight) in stale) {
            inFlight.remove(id)
            returned[id] = flight.files
            putBack(flight.files)
        }
        while (returned.size > 20) returned.remove(returned.keys.first())
    }

    /** Files in flight, waiting for a page's answer. */
    val unanswered: Int get() = inFlight.values.sumOf { it.files.size }

    private fun keeps(result: AttachedFile?): Boolean =
        result == null || (!result.taken && result.reason !in setOf("refused", "removed"))

    /** Files or dictation wait for a composer to connect: a New chat already on screen must load again to take them. */
    val waiting: Boolean get() = files != null || dictation

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

/** One file of the page's `attached`: whether it was taken, and why not (`locked`, `unavailable`, `failed`, `refused`, `removed`). */
@Serializable
data class AttachedFile(val name: String? = null, val taken: Boolean = false, val reason: String? = null)
