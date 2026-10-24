package com.chatwithwork.app.main

import android.os.SystemClock
import androidx.lifecycle.ViewModel

/** State the tab shell keeps across configuration changes. */
class MainViewModel : ViewModel() {
    var selectedTab: MainTab = MainTab.CHATS

    /** The organization the tabs show, from the last page that named one. */
    var account: String? = null

    /**
     * The organization the tabs start in after the person moved to it; null
     * while they start where the server sends them.
     */
    var tabsAccount: String? = null

    /** Nobody is signed in: the welcome screen covers the tabs. */
    var signedOut = false

    /** The sign-in modal is up (or on its way). */
    var signingIn = false

    /** A link to open once its tab (and the session) is ready. */
    var pendingLink: String? = null

    /**
     * The waiting link, taken if it opens in [navigatorName]'s tab and that
     * navigator can show it now ([navigatorReady]); otherwise it keeps
     * waiting for the tab's first page (a cold start from a notification).
     */
    fun takePendingLink(navigatorName: String, navigatorReady: Boolean, currentTab: MainTab): String? {
        val url = pendingLink ?: return null
        val tab = MainTab.forUrl(url) ?: currentTab
        if (navigatorName != tab.configuration.name || !navigatorReady) return null
        pendingLink = null
        return url
    }

    private val createdAt = SystemClock.elapsedRealtime()
    var firstPageShown = false

    /** Keep the splash until the first page is drawn, but never long. */
    fun keepSplash(): Boolean = !firstPageShown && SystemClock.elapsedRealtime() - createdAt < SPLASH_MAX_MS

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}
