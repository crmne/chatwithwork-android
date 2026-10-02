package com.chatwithwork.app.fragments

import android.webkit.WebView

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
            if (window.TurboSession) window.TurboSession.elementTouchStarted(false)
          }, { passive: true })
        })()
        """.trimIndent()

    fun install(webView: WebView) {
        webView.evaluateJavascript(script, null)
    }
}
