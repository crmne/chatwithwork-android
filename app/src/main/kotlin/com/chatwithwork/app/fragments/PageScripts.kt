package com.chatwithwork.app.fragments

import android.view.View
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * A little JavaScript the app adds to every page it loads.
 *
 * Pull to refresh: Hotwire Native turns the pull off whenever a touch starts
 * inside an element that can scroll, even one already scrolled to its top.
 * Chat with Work's pages scroll inside `.app-main`, not the document, so
 * without this the pull would never work on a list long enough to scroll.
 * This listener allows the pull when every scrollable element under the
 * finger is at its top. It's added on `window`, so it runs after Hotwire's
 * listener on `document` and has the last word. Pages can still opt out of
 * the pull with `data-native-prevent-pull-to-refresh`.
 */
object PageScripts {
    private val script =
        """
        (() => {
          if (window.__chatWithWorkNative) return
          window.__chatWithWorkNative = true

          window.addEventListener("touchstart", (event) => {
            let element = event.target instanceof Element ? event.target : null
            while (element) {
              if (element.closest("[data-native-prevent-pull-to-refresh]")) return
              const overflowY = window.getComputedStyle(element).overflowY
              const scrollable = element.scrollHeight > element.clientHeight &&
                (overflowY === "auto" || overflowY === "scroll")
              if (scrollable && element.scrollTop > 0) return
              element = element.parentElement
            }
            // Hotwire Native 1.4 reaches the app through TurboSessionChannel; earlier versions had window.TurboSession.
            if (window.TurboSessionChannel) {
              window.TurboSessionChannel.postMessage(JSON.stringify({ name: "elementTouchStarted", args: [ false ] }))
            } else if (window.TurboSession) {
              window.TurboSession.elementTouchStarted(false)
            }
          }, { passive: true })
        })()
        """.trimIndent()

    /** The page's composer takes the focus, where the app draws none of its own (`web-composer`). */
    const val FOCUS_COMPOSER =
        "document.querySelector(\"#new_message [data-chat-target~='input']\")?.focus({ preventScroll: true })"

    fun install(webView: WebView) {
        webView.evaluateJavascript(script, null)
    }

    /**
     * Tells the page how much of its top the app's bar covers, in the page's
     * own pixels: a chat runs under the bar and keeps what it opens with
     * clear of it (docs/server-contract.md). 0 where the page starts below it.
     * Where it [floats] over a page that draws what's behind it, `<html>`
     * says so (`data-native-top-bar="floating"`), and the page fades what
     * scrolls up under the bar (TopBar).
     */
    fun setTopBarHeight(webView: WebView, height: Int, floats: Boolean = false) {
        // A web view not laid out yet (a screen coming back after Android ended the app, the shared web view
        // between screens) has no width to scale by: it's told once it has one, where it was never told and the
        // page ran under the bar.
        whenLaidOut(webView) { laidOut, width ->
            (laidOut as WebView).evaluateJavascript(topBarHeightScript(height, width, floats), null)
        }
    }

    /**
     * Runs [action] with [view] and its width now, or once it has one. A
     * newer action for the same view takes the place of one still waiting.
     * The action gets the view rather than holding it, and the waiting list
     * holds its listener weakly (the view's own listeners hold it), so a view
     * dropped before it's laid out is never kept by the list.
     */
    internal fun whenLaidOut(view: View, action: (View, Int) -> Unit) {
        waiting.remove(view)?.get()?.let(view::removeOnLayoutChangeListener)
        if (view.width > 0) return action(view, view.width)

        val listener =
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    laidOut: View,
                    l: Int,
                    t: Int,
                    r: Int,
                    b: Int,
                    ol: Int,
                    ot: Int,
                    or: Int,
                    ob: Int
                ) {
                    if (laidOut.width <= 0) return
                    laidOut.removeOnLayoutChangeListener(this)
                    waiting.remove(laidOut)
                    action(laidOut, laidOut.width)
                }
            }
        waiting[view] = WeakReference(listener)
        view.addOnLayoutChangeListener(listener)
    }

    private val waiting = WeakHashMap<View, WeakReference<View.OnLayoutChangeListener>>()

    internal fun topBarHeightScript(height: Int, viewWidth: Int, floats: Boolean = false): String =
        "document.documentElement.style" +
            ".setProperty('--native-top-bar-height', ($height * window.innerWidth / $viewWidth) + 'px');" +
            if (floats) {
                "document.documentElement.dataset.nativeTopBar = 'floating'"
            } else {
                "delete document.documentElement.dataset.nativeTopBar"
            }
}
