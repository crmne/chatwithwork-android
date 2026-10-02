package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.routing.AppUrls

/**
 * One push, as the server sends it: an FCM data message (docs/push-notifications.md).
 * The same keys as the iOS payload, with the alert's text as plain data.
 * Unknown kinds and paths that aren't a page of this app are dropped.
 */
data class Push(val kind: Kind, val url: String, val thread: String, val title: String?, val body: String?) {
    enum class Kind(val wire: String) {
        APPROVAL_WAITING("approval_waiting"),
        INPUT_REQUESTED("input_requested"),

        /** The change was approved, denied, or answered elsewhere: take the notification away. */
        RESOLVED("resolved")
    }

    val defaultBody: Int
        get() = if (kind == Kind.INPUT_REQUESTED) R.string.push_input_body else R.string.push_approval_body

    companion object {
        fun from(data: Map<String, String>): Push? {
            val kind = Kind.entries.firstOrNull { it.wire == data["kind"] } ?: return null
            val path = data["path"]?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: return null
            val url = AppConfig.baseUrl + path
            if (!AppUrls.isAppUrl(url)) return null
            return Push(
                kind = kind,
                url = url,
                thread = data["thread"]?.takeIf { it.isNotBlank() } ?: path,
                title = data["title"]?.takeIf { it.isNotBlank() },
                body = data["body"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}
