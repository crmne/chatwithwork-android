package com.chatwithwork.app.push

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One answer as the server names it (docs/server-contract.md, "Live Updates"): opaque, and nothing from the chat. */
data class LiveRunRef(val id: String, val startedAt: Long?, val revision: Long)

/**
 * What this phone remembers of the run each chat's Live Update follows, one
 * per thread, whether or not its card is showing: the run's start, so the
 * timer never restarts when the card goes and comes back or the process
 * does; the newest revision heard, so a late message can't undo a newer one;
 * and whether the person dismissed it, which holds for that run only.
 *
 * A timestamp can't say which answer a message belongs to, so messages that
 * name a run are judged here. Messages that don't come from an older server,
 * and keep the sent-at watermark in [LiveUpdates].
 */
class LiveRuns(private val read: (String) -> String?, private val write: (String, String) -> Unit) {
    @Serializable
    data class Record(
        val run: String,
        val startedAt: Long,
        val revision: Long = 0,
        val sentAt: Long = 0,
        val ended: Boolean = false,
        val dismissed: Boolean = false
    )

    /** What a card showing now should do with a message. */
    enum class Admission { SHOW, DROP }

    fun record(thread: String): Record? =
        read(key(thread))?.let { runCatching { json.decodeFromString<Record>(it) }.getOrNull() }

    /**
     * Showing [run]'s card: its start, the same one every time, or null once
     * the person dismissed it. A run this phone hasn't seen replaces the
     * last, so a later answer always shows.
     */
    fun starting(thread: String, run: LiveRunRef, now: Long): Long? {
        val known = record(thread)?.takeIf { it.run == run.id }
        if (known?.dismissed == true) return null
        val record =
            known?.copy(
                revision = maxOf(known.revision, run.revision),
                ended = known.ended && run.revision <= known.revision
            ) ?: Record(run.id, run.startedAt ?: now, run.revision)
        save(thread, record)
        return record.startedAt
    }

    /**
     * Whether a card takes this message, which names run [run]. [showing]
     * says a card without a run stands in this slot (shown before the server
     * said runs), which adopts the first run it hears. Another run's message
     * never reaches this slot's card; an older one in this run neither, and
     * one at the same revision only if it's newer and nothing ended there.
     */
    fun admit(thread: String, run: String, progress: LiveProgress, showing: Boolean): Admission {
        val known = record(thread)
        if (known == null) {
            if (showing) save(thread, heard(progress, run))
            return if (showing) Admission.SHOW else Admission.DROP
        }
        if (known.run != run) return Admission.DROP
        val newer =
            progress.revision > known.revision ||
                (progress.revision == known.revision && !known.ended && progress.sentAt > known.sentAt)
        if (!newer) return Admission.DROP
        save(
            thread,
            known.copy(revision = progress.revision, sentAt = progress.sentAt, ended = progress.isEnd)
        )
        return if (known.dismissed) Admission.DROP else Admission.SHOW
    }

    /**
     * The system took [run]'s card away, posted at [postedAt], which is the
     * person's doing unless the card timed out. A card of an older run says
     * nothing about the one following it.
     */
    fun dismissed(thread: String, run: String, postedAt: Long, now: Long, timeout: Long) {
        val known = record(thread)?.takeIf { it.run == run } ?: return
        if (now - postedAt < timeout) save(thread, known.copy(dismissed = true))
    }

    private fun heard(progress: LiveProgress, run: String) = Record(
        run = run,
        startedAt = progress.startedAt ?: progress.sentAt,
        revision = progress.revision,
        sentAt = progress.sentAt,
        ended = progress.isEnd
    )

    private fun save(thread: String, record: Record) = write(key(thread), json.encodeToString(record))

    private fun key(thread: String) = "run:$thread"

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
