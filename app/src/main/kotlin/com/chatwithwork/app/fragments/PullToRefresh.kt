package com.chatwithwork.app.fragments

import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.WeakHashMap

/**
 * Pulling a page down at its top brings it up to date in place, as the page
 * does itself coming back from the background: `window.chatWithWork.refresh()`
 * morphs a fresh copy of a chat's frame in, keeping what's typed, the focus
 * and the scroll, and refreshes any other page by morphing it (server
 * contract, "Pull to refresh"). Hotwire Native's own refresh loads the page
 * again from scratch, which ran every entrance again and took the keyboard
 * away. The spinner stays until the page says it's done (`refreshing`), at
 * most [GIVE_UP_MS], timed natively so a page that never answers can't keep
 * it. A server without the call gets Hotwire Native's refresh.
 */
object PullToRefresh {
    private const val START =
        "(function(){var c=window.chatWithWork;if(!c||!c.refresh)return false;c.refresh();return true})()"
    private const val BUSY = "!!(window.chatWithWork&&window.chatWithWork.refreshing)"
    private const val POLL_MS = 100L
    private const val GIVE_UP_MS = 10_000L

    /** The pull each spinner is showing, so only its own answer or give-up stops it. */
    private val pulls = WeakHashMap<SwipeRefreshLayout, Any>()

    /** Takes over [spinner]'s refresh, which Hotwire Native sets again each time it prepares the screen. */
    fun install(spinner: SwipeRefreshLayout, webView: WebView, fallback: () -> Unit) {
        spinner.setOnRefreshListener {
            // Each pull is its own: a page that never answers can't keep the spinner, and an answer that comes
            // after the give-up, or after another pull, does nothing.
            val pull = Any()
            pulls[spinner] = pull
            val giveUp = Runnable { if (pulls[spinner] === pull) stop(spinner) }
            spinner.postDelayed(giveUp, GIVE_UP_MS)
            webView.evaluateJavascript(START) { started ->
                if (pulls[spinner] !== pull) return@evaluateJavascript
                if (started == "true") {
                    stopWhenDone(spinner, webView, pull)
                } else {
                    spinner.removeCallbacks(giveUp)
                    stop(spinner)
                    fallback()
                }
            }
        }
    }

    private fun stopWhenDone(spinner: SwipeRefreshLayout, webView: WebView, pull: Any) {
        spinner.postDelayed({
            if (pulls[spinner] !== pull) return@postDelayed
            webView.evaluateJavascript(BUSY) { busy ->
                if (pulls[spinner] !== pull) return@evaluateJavascript
                if (busy == "true") stopWhenDone(spinner, webView, pull) else stop(spinner)
            }
        }, POLL_MS)
    }

    private fun stop(spinner: SwipeRefreshLayout) {
        pulls.remove(spinner)
        spinner.isRefreshing = false
    }
}
