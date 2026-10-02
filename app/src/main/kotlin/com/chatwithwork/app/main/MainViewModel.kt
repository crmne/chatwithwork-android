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

    private val createdAt = SystemClock.elapsedRealtime()
    var firstPageShown = false

    /** Keep the splash until the first page is drawn, but never long. */
    fun keepSplash(): Boolean = !firstPageShown && SystemClock.elapsedRealtime() - createdAt < SPLASH_MAX_MS

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}
