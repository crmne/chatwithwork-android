package com.chatwithwork.app.main

import android.content.Intent
import com.chatwithwork.app.routing.AppUrls

/**
 * The link an intent asks the app to open: a notification's tap, an App
 * Link, or the end of a browser sign-in (chatwithwork://...). Anything else
 * (another host, a missing link, the launcher) opens nothing.
 */
object LaunchLinks {
    /**
     * [restored] is true when the activity is recreated from saved state.
     * Then, and when Android relaunches the app from Recents, the intent is
     * the one the activity first started with: its link opened back then,
     * and opening it again would push the chat a second time, or bring back
     * a notification tapped hours ago.
     */
    fun from(action: String?, data: String?, flags: Int, restored: Boolean): String? {
        if (action != Intent.ACTION_VIEW || data == null) return null
        if (restored || flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return data.takeIf { AppUrls.isAppUrl(it) || AppUrls.isAuthCallback(it) }
    }

    /**
     * What another app shared (ACTION_SEND, text/plain): its text and,
     * if it gave one, its subject, which the new chat page joins into the
     * composer (`title`, `text`). Null when there's nothing to share.
     */
    fun shared(action: String?, type: String?, text: CharSequence?, subject: CharSequence?): Shared? {
        if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
        val body = text?.toString()?.trim()?.take(SHARED_LIMIT).orEmpty()
        if (body.isEmpty()) return null
        return Shared(text = body, title = subject?.toString()?.trim()?.take(SHARED_LIMIT)?.takeIf { it.isNotEmpty() })
    }

    data class Shared(val text: String, val title: String?) {
        /** [newChat] (a new chat page's URL) with the shared words as its query. */
        fun into(newChat: String): String {
            val query =
                listOfNotNull(title?.let { "title=" + encode(it) }, "text=" + encode(text)).joinToString("&")
            return newChat + (if (newChat.contains('?')) "&" else "?") + query
        }

        private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    /** More than a composer takes is more than a URL should carry. */
    private const val SHARED_LIMIT = 4000
}
