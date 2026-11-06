package com.chatwithwork.app.fragments

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chatwithwork.app.AppConfig

/**
 * Tells the app when a new document in the shared web view first paints, the
 * moment a cold boot's page takes over from a chat's skeleton. Hotwire Native
 * only hears of it after the document's load event, its scripts and pictures
 * included, and a web view without these features still gets that.
 *
 * Only the app's own server can send the message, and it carries nothing.
 */
object FirstPaint {
    /** Who wants to hear of the next first paint: the screen showing a skeleton. */
    var listener: (() -> Unit)? = null

    fun install(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return
        }
        val origins = setOf(AppConfig.baseUrl)
        WebViewCompat.addWebMessageListener(webView, BRIDGE, origins) { _, _, _, _, _ -> listener?.invoke() }
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, origins)
    }

    /** Runs [action] once the web view's frame holding what's in its page now is ready to draw. */
    fun afterNextFrame(webView: WebView, action: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
            WebViewCompat.postVisualStateCallback(webView, VISUAL_STATE_REQUEST) { action() }
        } else {
            action()
        }
    }

    private const val BRIDGE = "cwwFirstPaint"
    private const val VISUAL_STATE_REQUEST = 0x5CE1L

    /** Runs before anything else in every document: only the top page's first contentful paint counts. */
    private const val SCRIPT = """
        if (window === window.top && window.PerformanceObserver) {
          new PerformanceObserver(function (entries, observer) {
            if (entries.getEntriesByName("first-contentful-paint").length) {
              observer.disconnect();
              cwwFirstPaint.postMessage("painted");
            }
          }).observe({ type: "paint", buffered: true });
        }
    """
}
