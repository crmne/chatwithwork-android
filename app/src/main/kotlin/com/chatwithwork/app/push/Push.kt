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
        ANSWERED("answered"),

        /** A bot handed a conversation to the person's team. */
        HANDED_OFF("handed_off"),

        /** A chat couldn't finish its answer. */
        FAILED("failed"),

        /** The change was approved, denied, or answered elsewhere: take the notification away. */
        RESOLVED("resolved")
    }

    val defaultBody: Int
        get() = when (kind) {
            Kind.INPUT_REQUESTED -> R.string.push_input_body
            Kind.ANSWERED -> R.string.push_answered_body
            Kind.HANDED_OFF -> R.string.push_handed_off_body
            Kind.FAILED -> R.string.push_failed_body
            Kind.APPROVAL_WAITING, Kind.RESOLVED -> R.string.push_approval_body
        }

    /** Whether it waits for the person (an approval, a question): those ring, on their own channel. */
    val waitsForPerson: Boolean
        get() = kind == Kind.APPROVAL_WAITING || kind == Kind.INPUT_REQUESTED

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
