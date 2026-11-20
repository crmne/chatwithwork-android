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
    /**
     * A file shared in, with an identity of its own while it waits, so two
     * shares of the same bytes stay two, and a late answer about one attempt
     * finds it wherever it is now.
     */
    private class Entry(val key: String, val file: ComposerFile) {
        /** The attach it's in flight with, until the page answers for it. */
        var attempt: String? = null
        var sentAt = 0L

        /** The screen whose page has it in flight. */
        var owner = 0

        /** Waits for a visit after this one: an upload that failed here isn't tried again on the same page. */
        var afterVisit = -1L
    }

    private val entries = mutableListOf<Entry>()

    /** Each attach's files by key, in its order, for the page's answer: kept a while after they came back unanswered. */
    private val attempts = LinkedHashMap<String, List<String>>()
    private var visits = 0L
    private var dictation = false
    private var focus = false

    /** How long files wait for a page's answer: long enough for a large upload on a slow network. */
    const val UNANSWERED_MS = 10 * 60 * 1000L

    /** Shared files, after any still waiting. */
    fun putFiles(files: List<ComposerFile>) {
        entries += files.map { Entry(UUID.randomUUID().toString(), it) }
    }

    /** For a page that doesn't answer for them: every file not in flight, handed over as the reply goes. */
    fun takeFiles(): List<ComposerFile>? {
        val ready = entries.filter { it.attempt == null }
        if (ready.isEmpty()) return null
        entries.removeAll(ready)
        return ready.map { it.file }
    }

    /** Files a composer was handed and couldn't take, ahead of any shared since. */
    fun putBack(taken: List<ComposerFile>) {
        entries.addAll(0, taken.map { Entry(UUID.randomUUID().toString(), it) })
    }

    /**
     * For a page that answers for them (`confirmsAttach`): the files it may
     * have now, in flight under a new id until its `attached` ([settle]).
     * [connecting] is false for a composer that only changed.
     */
    fun sendFiles(
        owner: Int = 0,
        connecting: Boolean = true,
        now: Long = System.currentTimeMillis()
    ): Pair<String, List<ComposerFile>>? {
        // Files kept for the next New chat go only to a composer connecting after a later visit, never to one that
        // just changed (a menu, an unlock) on the page that failed them.
        val ready = entries.filter {
            it.attempt == null && (it.afterVisit < 0 || (connecting && it.afterVisit < visits))
        }
        if (ready.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        ready.forEach {
            it.attempt = id
            it.sentAt = now
            it.owner = owner
        }
        attempts[id] = ready.map { it.key }
        while (attempts.size > 50) attempts.remove(attempts.keys.first())
        return id to ready.map { it.file }
    }

    /** The attach [id] never reached the page: its files wait again at once. */
    fun unsend(id: String) {
        attempts.remove(id)
        entries.filter { it.attempt == id }.forEach { it.attempt = null }
    }

    /**
     * The page's answer for the attach [id], file by file in its order. Taken,
     * `refused` and `removed` leave, wherever the file is now (an answer that
     * comes after the file was given back, or sent again, still counts).
     * `locked` and `unavailable` wait to be handed again, on this page once it
     * unlocks; `failed`, a reason not known yet, or no word wait for the next
     * New chat.
     */
    fun settle(id: String?, results: List<AttachedFile>) {
        val keys = id?.let { attempts.remove(it) } ?: return
        keys.forEachIndexed { index, key ->
            val entry = entries.find { it.key == key } ?: return@forEachIndexed
            val result = results.getOrNull(index)
            when {
                result != null && (
                    result.taken || result.reason in setOf(
                        "refused",
                        "removed"
                    )
                    ) -> entries.remove(entry)

                // Given back or sent again since: its newer attempt decides.
                entry.attempt != id -> Unit

                result?.reason in setOf("locked", "unavailable") -> {
                    entry.attempt = null
                    entry.afterVisit = -1
                }

                else -> {
                    entry.attempt = null
                    entry.afterVisit = visits
                }
            }
        }
    }

    /**
     * Gives back files in flight that no answer can come for: those of the
     * screen [owner] whose page went away, those sent before [olderThan], or
     * all of them. They wait for the next New chat; a late answer still
     * takes out what the page took.
     */
    fun returnUnanswered(owner: Int? = null, olderThan: Long? = null) {
        entries.filter {
            it.attempt != null && (owner == null || it.owner == owner) && (olderThan == null || it.sentAt < olderThan)
        }.forEach {
            it.attempt = null
            it.afterVisit = visits
        }
    }

    /**
     * The screen [owner] starts a visit: files its page had in flight come
     * back, and every file kept for the next New chat may go to the page
     * this or a later visit draws.
     */
    fun visitStarted(owner: Int) {
        returnUnanswered(owner = owner)
        visits++
    }

    /** Files in flight, waiting for a page's answer. */
    val unanswered: Int get() = entries.count { it.attempt != null }

    /** Files or dictation wait for a composer to connect: a New chat already on screen must load again to take them. */
    val waiting: Boolean get() = entries.any { it.attempt == null } || dictation

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
