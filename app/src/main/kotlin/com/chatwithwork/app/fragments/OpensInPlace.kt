package com.chatwithwork.app.fragments

import android.os.SystemClock
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.turbo.nav.Presentation
import dev.hotwire.core.turbo.visit.VisitAction

/**
 * New chat and New bot's sentence opened from a page (the bar's New chat, a
 * link on the page): the page turns into them in place, as from the sidebar,
 * with no screen sliding in and no skeleton, the web view going on showing the
 * page until New chat draws. Underneath it's still a push, so Back returns to
 * the page it was opened from, and the chat a question starts replaces it
 * (NewChatHandOff), so Back from that chat returns there too, never to an
 * empty New chat.
 */
object OpensInPlace {
    private var expected: String? = null
    private var expectedAt = 0L

    /** Whether a visit to [location] from a page is pushed in place. */
    fun applies(location: String, toModal: Boolean, presentation: Presentation, action: VisitAction): Boolean =
        AppUrls.opensAsPlace(location) &&
            !toModal &&
            action == VisitAction.ADVANCE &&
            (presentation == Presentation.DEFAULT || presentation == Presentation.PUSH) &&
            !NewChatHandOff.isExpected(location)

    fun expect(location: String, now: Long = SystemClock.uptimeMillis()) {
        expected = location
        expectedAt = now
    }

    /** Whether the screen just made for [location] is the one opened in place, taken once. */
    fun take(location: String, now: Long = SystemClock.uptimeMillis()): Boolean {
        val taken = expected == location && now - expectedAt in 0..EXPECTED_FOR_MS
        if (taken || now - expectedAt > EXPECTED_FOR_MS) expected = null
        return taken
    }

    private const val EXPECTED_FOR_MS = 5_000L
}
