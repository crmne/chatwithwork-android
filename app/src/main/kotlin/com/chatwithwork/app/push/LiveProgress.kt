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
 * ended in generic words. [sentAt] is the server's clock, so an older message
 * arriving late never undoes a newer one.
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
    val recentSteps: List<String> = emptyList()
) {
    /** Still answering; any other phase is how it ended. */
    val isWorking: Boolean get() = phase == ANSWERING

    companion object {
        const val ANSWERING = "answering"
        val STEP_KINDS = setOf("search", "list", "read", "use", "plan", "run", "ask", "write", "answer")

        /** Only fixed kinds may become visible history, never arbitrary payload text. */
        fun recentSteps(encoded: String?, current: String?): List<String> {
            val parsed = if (encoded != null && encoded.length <= 256) {
                runCatching { Json.parseToJsonElement(encoded) as? JsonArray }.getOrNull()
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
                    ?.filter { it in STEP_KINDS }.orEmpty()
            } else {
                emptyList()
            }
            return (parsed + listOfNotNull(current?.takeIf { it in STEP_KINDS }))
                .fold(emptyList<String>()) { result, kind ->
                    if (result.lastOrNull() ==
                        kind
                    ) {
                        result
                    } else {
                        result + kind
                    }
                }
                .takeLast(3)
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
                recentSteps = recentSteps(data["recent_steps"], data["step"])
            )
        }
    }
}
