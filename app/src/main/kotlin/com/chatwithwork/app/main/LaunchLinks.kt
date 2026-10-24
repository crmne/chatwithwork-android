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
}
