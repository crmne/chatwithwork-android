package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.routing.AppUrls

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
    val sentAt: Long
) {
    /** Still answering; any other phase is how it ended. */
    val isWorking: Boolean get() = phase == ANSWERING

    companion object {
        const val ANSWERING = "answering"

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
                sentAt = data["sent_at"]?.toLongOrNull() ?: 0
            )
        }
    }
}
