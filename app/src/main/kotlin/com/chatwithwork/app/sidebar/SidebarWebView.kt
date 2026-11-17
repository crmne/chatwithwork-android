package com.chatwithwork.app.sidebar

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.routing.AppUrls

/**
 * The server's sidebar page in the drawer (docs/server-contract.md, "The
 * sidebar page"): one web view, kept for the activity's life, over the native
 * sidebar, which shows until the page has drawn and stays the fallback (a
 * server without the page, or a page that never loaded). The page is the web
 * sidebar itself, live on its own streams, so the app only draws the drawer
 * around it: what it opens goes to [Listener], and nothing but the page ever
 * loads here.
 */
@SuppressLint("SetJavaScriptEnabled")
class SidebarWebView(private val container: FrameLayout, private val native: View, private val listener: Listener) {
    interface Listener {
        /** A page of this server, to open as a place. */
        fun onSidebarVisit(url: String)

        /** Another site, for the browser. */
        fun onSidebarExternal(url: String)

        fun onSidebarClose()

        fun onSidebarConfirm(confirm: SidebarPage.Message.Confirm)

        /** Drawn: answer with the page on screen ([current]). */
        fun onSidebarReady()

        /** The server has no sidebar page (an older one): the native sidebar it is. */
        fun onSidebarMissing()
    }

    private val main = Handler(Looper.getMainLooper())

    /** The page's path ("/482139075/sidebar"), once one was asked for. */
    var pagePath: String? = null
        private set

    /** Whether the page drew since it was last loaded, and so shows in place of the native sidebar. */
    var isShowing = false
        private set

    private var failed = false

    private var webView: WebView = makeWebView()

    private fun makeWebView(): WebView = WebView(container.context).apply {
        layoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(ContextCompat.getColor(context, R.color.lw_canvas_sunken))
        isVisible = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            userAgentString = SidebarPage.userAgent(
                AppConfig.userAgentPrefix(context),
                WebSettings.getDefaultUserAgent(context)
            )
        }
        addJavascriptInterface(Bridge(), SidebarPage.INTERFACE)
        webViewClient = Client()
    }

    init {
        container.addView(webView)
    }

    /** Shows the page at [path], loading it unless it's the one already there; null goes back to the native sidebar. */
    fun show(path: String?) {
        if (path == null) {
            reset()
            return
        }
        if (path == pagePath && !failed) return
        pagePath = path
        isShowing = false
        failed = false
        apply()
        webView.loadUrl(AppUrls.absolute(path))
    }

    /** The drawer opening: a page that couldn't load tries again. */
    fun retryIfFailed() {
        val path = pagePath ?: return
        if (failed) {
            failed = false
            webView.loadUrl(AppUrls.absolute(path))
        }
    }

    /** Forgets what it showed (signed out, or signed in as someone else): the native sidebar until the page draws again. */
    fun reset() {
        isShowing = false
        failed = false
        // Asked for again by the next connect.
        pagePath = null
        webView.loadUrl("about:blank")
        apply()
    }

    /** Marks the row of the page on the app's screen. */
    fun current(url: String, belongsTo: String?) {
        if (isShowing) webView.evaluateJavascript(SidebarPage.Script.current(url, belongsTo), null)
    }

    /** Back from the background, where the page's streams may have missed something. */
    fun refresh() {
        if (isShowing) webView.evaluateJavascript(SidebarPage.Script.REFRESH, null)
    }

    fun answer(id: String, confirmed: Boolean) {
        webView.evaluateJavascript(SidebarPage.Script.answer(id, confirmed), null)
    }

    private fun apply() {
        webView.isVisible = isShowing
        native.isVisible = !isShowing
    }

    private fun receive(message: SidebarPage.Message) {
        when (message) {
            SidebarPage.Message.Ready -> {
                // Also after the offline WebView's error and then the worker's copy.
                failed = false
                isShowing = true
                apply()
                listener.onSidebarReady()
            }

            is SidebarPage.Message.Visit -> route(message.url)

            SidebarPage.Message.Close -> listener.onSidebarClose()

            is SidebarPage.Message.Confirm -> listener.onSidebarConfirm(message)
        }
    }

    private fun route(url: String) {
        when (SidebarPage.load(url, pagePath)) {
            SidebarPage.Load.ALLOW -> Unit
            SidebarPage.Load.VISIT -> listener.onSidebarVisit(url)
            SidebarPage.Load.EXTERNAL -> listener.onSidebarExternal(url)
        }
    }

    /** The page's messages arrive on WebView's own thread, and only from our server's page. */
    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(raw: String) {
            val message = SidebarPage.parse(raw) ?: return
            main.post {
                val shown = webView.url
                if (shown != null && AppUrls.isAppUrl(shown)) receive(message)
            }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            return when (SidebarPage.load(url, pagePath)) {
                SidebarPage.Load.ALLOW -> false

                SidebarPage.Load.VISIT -> {
                    // A redirect on the page's own load (no session: the sign-in form) isn't the person's tap;
                    // the navigator finds out about the session on its own.
                    if (request.isRedirect && AppUrls.isSignInForm(url)) {
                        failed = true
                    } else {
                        listener.onSidebarVisit(url)
                    }
                    true
                }

                SidebarPage.Load.EXTERNAL -> {
                    listener.onSidebarExternal(url)
                    true
                }
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame || isShowing) return
            failed = true
            if (response.statusCode == HTTP_NOT_FOUND) listener.onSidebarMissing()
        }

        // Its renderer gone (memory pressure, a crash): a new web view, and the page again as the drawer next opens.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            container.removeView(view)
            view.destroy()
            webView = makeWebView()
            container.addView(webView)
            isShowing = false
            failed = pagePath != null
            apply()
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            // Offline, the worker's copy may still draw after this: `ready` then shows it.
            if (request.isForMainFrame && !isShowing) failed = true
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
