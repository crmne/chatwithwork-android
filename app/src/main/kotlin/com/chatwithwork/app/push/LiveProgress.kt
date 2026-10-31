package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.routing.AppUrls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * A step of an answer a Live Update follows, or how it ended, as the server
 * sends it: FCM data of kind `progress` (docs/server-contract.md, "Live
 * Updates"). Never the chat's words: [message] names the kind of step and a
 * built-in service ("Searching Todoist", "Writing the answer"), or says how it
 * ended in generic words. [run] says which answer it's about and [revision]
 * orders it within that answer ([LiveRuns]); both are absent from older
 * servers, where [sentAt], the server's clock, keeps an older message arriving
 * late from undoing a newer one. [startedAt] is when the answer began.
 */
data class LiveProgress(
    val url: String,
    val thread: String,
    val phase: String,
    val message: String?,
    val step: String?,
    val steps: Int,
    val sentAt: Long,
    val notificationId: String? = null,
    val run: String? = null,
    val revision: Long = 0,
    val startedAt: Long? = null,
    val waitingSince: Long? = null,
    val service: String? = null,
    val services: List<String> = emptyList(),
    val trail: String? = null,
    val pace: String? = null,
    val agents: Int? = null,
    val heardAt: Long? = null
) {
    /** Still answering. */
    val isWorking: Boolean get() = phase == ANSWERING

    /** Stopped for the person: the same card stays, and the answer goes on after. */
    val isWaiting: Boolean get() = phase in WAITING

    /** Only answered, failed, and stopped end a run. */
    val isEnd: Boolean get() = !isWorking && !isWaiting

    val runRef: LiveRunRef? get() = run?.let { LiveRunRef(it, startedAt, revision) }

    companion object {
        const val ANSWERING = "answering"
        val WAITING = setOf("approval_waiting", "input_requested")
        private const val TRAIL_LETTERS = "slrupxaw"
        private const val TRAIL_LENGTH = 48
        private const val PACE_DIGITS = "1234"

        private fun stringList(encoded: String?): List<String> = if (encoded != null && encoded.length <= 256) {
            runCatching { Json.parseToJsonElement(encoded) as? JsonArray }.getOrNull()
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
                .orEmpty()
        } else {
            emptyList()
        }

        /** The steps so far, one letter each, upper case where one failed or was denied: nothing else passes. */
        fun trail(encoded: String?): String? =
            encoded?.filter { it.lowercaseChar() in TRAIL_LETTERS }?.takeLast(TRAIL_LENGTH)?.takeIf { it.isNotEmpty() }

        /**
         * How long each of the trail's steps took, one digit each (1: under 2
         * seconds, 2: under 10, 3: under 60, 4: a minute or more). Only whole:
         * a [pace] that doesn't say it of every step of [encoded] says nothing.
         */
        fun pace(pace: String?, encoded: String?): String? {
            if (pace == null || encoded == null || pace.length != encoded.length) return null
            if (pace.any { it !in PACE_DIGITS }) return null
            return encoded.zip(pace).filter { (letter, _) -> letter.lowercaseChar() in TRAIL_LETTERS }
                .takeLast(TRAIL_LENGTH).map { it.second }.joinToString("").takeIf { it.isNotEmpty() }
        }

        fun from(data: Map<String, String>): LiveProgress? {
            if (data["kind"] != "progress") return null
            val path = data["path"]?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: return null
            val url = AppConfig.baseUrl + path
            if (!AppUrls.isAppUrl(url)) return null
            return LiveProgress(
                url = url,
                thread = data["thread"]?.takeIf { it.isNotBlank() } ?: return null,
                phase = data["phase"]?.takeIf { it.isNotBlank() } ?: return null,
                message = data["message"]?.takeIf { it.isNotBlank() },
                step = data["step"]?.takeIf { it.isNotBlank() },
                steps = data["steps"]?.toIntOrNull() ?: 0,
                sentAt = data["sent_at"]?.toLongOrNull() ?: 0,
                notificationId = data["notification_id"]?.takeIf { it.isNotBlank() },
                run = data["run"]?.takeIf { it.isNotBlank() },
                revision = data["revision"]?.toLongOrNull() ?: 0,
                startedAt = data["started_at"]?.toLongOrNull()?.let { it * 1000 },
                waitingSince = data["waiting_since"]?.toLongOrNull()?.let { it * 1000 },
                service = data["service"]?.takeIf { it.isNotBlank() },
                services = stringList(data["services"]),
                trail = trail(data["trail"]),
                pace = pace(data["pace"], data["trail"]),
                agents = data["agents"]?.toIntOrNull(),
                heardAt = data["heard_at"]?.toLongOrNull()?.let { it * 1000 }
            )
        }
    }
}
