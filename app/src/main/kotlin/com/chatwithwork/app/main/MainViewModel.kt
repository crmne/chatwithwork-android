package com.chatwithwork.app.main

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.chatwithwork.app.sidebar.Sidebar

/** State the sidebar shell keeps across configuration changes. */
class MainViewModel : ViewModel() {
    /** The organization the app shows, from the last page that named one. */
    var account: String? = null

    /**
     * Where the navigator starts the next time it starts over: a chat or new
     * chat, after moving to another organization or from a notification.
     * Null starts on the remembered new chat.
     */
    var startLocation: String? = null

    /** Nobody is signed in: the welcome screen covers the app. */
    var signedOut = false

    /** The sign-in modal is up (or on its way). */
    var signingIn = false

    /** A link to open once the navigator (and the session) is ready. */
    var pendingLink: String? = null

    /** The sidebar the server last sent, for the organization it names. */
    var sidebar: Sidebar? = null

    /** The sidebar changed since it was last fetched (the page said so while it was closed). */
    var sidebarStale = true

    private val createdAt = SystemClock.elapsedRealtime()
    var firstPageShown = false

    /** Keep the splash until the first page is drawn, but never long. */
    fun keepSplash(): Boolean = !firstPageShown && SystemClock.elapsedRealtime() - createdAt < SPLASH_MAX_MS

    /** The waiting link, taken once the navigator can show it. */
    fun takePendingLink(navigatorReady: Boolean): String? {
        if (!navigatorReady) return null
        return pendingLink.also { pendingLink = null }
    }

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}
