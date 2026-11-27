package com.chatwithwork.app.fragments

import android.view.View
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

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
 *
 * And it notes what the page deleted ([DELETES]), so back never goes to it.
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

    /**
     * Every path a form on the page deleted (a chat from its own menu, or
     * from a row of the sidebar page), kept on `window` until the app takes
     * them ([TAKE_DELETED]) and drops them from the places back goes to
     * (PlaceHistory.forget). A method link's form says its method as
     * turbo-rails sends it, a POST with `_method`, as the sidebar page's own
     * `native_sidebar_controller.js` reads it. Pages of this one server
     * only: nothing leaves the page but paths.
     */
    val DELETES =
        """
        (() => {
          if (window.__chatWithWorkDeletes) return
          window.__chatWithWorkDeletes = true
          window.__chatWithWorkDeleted = []

          document.addEventListener("turbo:submit-end", (event) => {
            const { success, formSubmission } = event.detail || {}
            if (!success || !formSubmission) return
            const form = formSubmission.formElement
            const url = new URL(formSubmission.fetchRequest?.url || form?.action || "", window.location.href)
            if (url.origin !== window.location.origin) return
            const method = (url.searchParams.get("_method") || form?.querySelector("input[name=_method]")?.value ||
              form?.getAttribute("method") || "").toLowerCase()
            if (method === "delete") window.__chatWithWorkDeleted.push(url.pathname)
          })
        })()
        """.trimIndent()

    /** Takes what [DELETES] noted, as a JSON array of paths (evaluateJavascript's answer, [deletedPaths]). */
    const val TAKE_DELETED =
        "(() => { const deleted = window.__chatWithWorkDeleted || []; window.__chatWithWorkDeleted = []; return deleted })()"

    /** The paths in [TAKE_DELETED]'s answer; none from anything else (a page without the script answers null). */
    fun deletedPaths(answer: String?): List<String> = runCatching {
        Json.parseToJsonElement(answer ?: "null").jsonArray.mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf {
                it.startsWith("/") &&
                    !it.startsWith("//")
            }
        }
    }.getOrDefault(emptyList())

    /** The page's composer takes the focus, where the app draws none of its own (`web-composer`). */
    const val FOCUS_COMPOSER =
        "document.querySelector(\"#new_message [data-chat-target~='input']\")?.focus({ preventScroll: true })"

    /** Both scripts, the first ended, so the second isn't read as a call on its result. */
    internal val installed: String get() = script + ";\n" + DELETES

    fun install(webView: WebView) {
        webView.evaluateJavascript(installed, null)
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
