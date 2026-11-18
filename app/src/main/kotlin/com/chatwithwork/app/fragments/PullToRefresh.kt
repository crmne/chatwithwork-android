package com.chatwithwork.app.fragments

import android.os.SystemClock
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Pulling a page down at its top brings it up to date in place, as the page
 * does itself coming back from the background: `window.chatWithWork.refresh()`
 * morphs a fresh copy of a chat's frame in, keeping what's typed, the focus
 * and the scroll, and refreshes any other page by morphing it (server
 * contract, "Pull to refresh"). Hotwire Native's own refresh loads the page
 * again from scratch, which ran every entrance again and took the keyboard
 * away. The spinner stays until the page says it's done (`refreshing`), at
 * most [GIVE_UP_MS]. A server without the call gets Hotwire Native's refresh.
 */
object PullToRefresh {
    private const val START =
        "(function(){var c=window.chatWithWork;if(!c||!c.refresh)return false;c.refresh();return true})()"
    private const val BUSY = "!!(window.chatWithWork&&window.chatWithWork.refreshing)"
    private const val POLL_MS = 100L
    private const val GIVE_UP_MS = 10_000L

    /** Takes over [spinner]'s refresh, which Hotwire Native sets again each time it prepares the screen. */
    fun install(spinner: SwipeRefreshLayout, webView: WebView, fallback: () -> Unit) {
        spinner.setOnRefreshListener {
            webView.evaluateJavascript(START) { started ->
                if (started == "true") {
                    stopWhenDone(spinner, webView, SystemClock.uptimeMillis())
                } else {
                    spinner.isRefreshing = false
                    fallback()
                }
            }
        }
    }

    private fun stopWhenDone(spinner: SwipeRefreshLayout, webView: WebView, startedAt: Long) {
        spinner.postDelayed({
            webView.evaluateJavascript(BUSY) { busy ->
                if (busy == "true" && SystemClock.uptimeMillis() - startedAt < GIVE_UP_MS) {
                    stopWhenDone(spinner, webView, startedAt)
                } else {
                    spinner.isRefreshing = false
                }
            }
        }, POLL_MS)
    }
}
