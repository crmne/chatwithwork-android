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
import java.net.URI

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

        /** The page couldn't load, or reload: the native sidebar until it does (it tries again as the drawer opens). */
        fun onSidebarFailed()
    }

    private val main = Handler(Looper.getMainLooper())

    private val state = SidebarPageState()

    private var destroyed = false

    /** The page's path ("/482139075/sidebar"), once one was asked for. */
    val pagePath: String?
        get() = state.path

    /** Whether the page in the web view drew, and so shows in place of the native sidebar. */
    val isShowing: Boolean
        get() = state.showing

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
        addJavascriptInterface(Bridge(this), SidebarPage.INTERFACE)
        webViewClient = Client()
    }

    init {
        container.addView(webView)
    }

    /** Shows the page at [path], loading it unless it's the one already there; null goes back to the native sidebar. */
    fun show(path: String?) = load(state.show(path))

    /** The drawer opening: a page that couldn't load tries again. */
    fun retryIfFailed() {
        if (state.retry()) state.path?.let { webView.loadUrl(AppUrls.absolute(it)) }
    }

    /**
     * Forgets what it showed (signed out, or signed in as someone else): the
     * native sidebar until the next connect names a page and it draws.
     */
    fun reset() = load(state.forget())

    private fun load(change: SidebarPageState.Change) {
        apply()
        when (change) {
            SidebarPageState.Change.NONE -> Unit
            SidebarPageState.Change.LOAD -> state.path?.let { webView.loadUrl(AppUrls.absolute(it)) }
            SidebarPageState.Change.CLEAR -> webView.loadUrl("about:blank")
        }
    }

    /** Marks the row of the page on the app's screen. */
    fun current(url: String, belongsTo: String?) {
        if (isShowing) webView.evaluateJavascript(SidebarPage.Script.current(url, belongsTo), null)
    }

    /** Back from the background, where the page's streams may have missed something. */
    fun refresh() {
        if (isShowing) webView.evaluateJavascript(SidebarPage.Script.REFRESH, null)
    }

    /**
     * The drawer closed (a back gesture, a swipe, the scrim, a visit): the
     * page ends picking rows and closes its menus, as the web's drawer does.
     */
    fun closed() {
        if (isShowing) webView.evaluateJavascript(SidebarPage.Script.CLOSED, null)
    }

    /**
     * The activity ending (a theme change makes another): stops the page and
     * its streams, and lets go of the web view, which holds the activity.
     * Nothing it posted runs after this.
     */
    fun destroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        container.removeView(webView)
        webView.destroy()
    }

    fun answer(id: String, confirmed: Boolean) {
        webView.evaluateJavascript(SidebarPage.Script.answer(id, confirmed), null)
    }

    private fun apply() {
        webView.isVisible = isShowing
        native.isVisible = !isShowing
    }

    private fun receive(message: SidebarPage.Message, document: Int, shown: String) {
        when (message) {
            SidebarPage.Message.Ready -> {
                // Only the page asked for, drawn in this load: also after the offline WebView's error and then the
                // worker's copy, or a reload that failed and then drew.
                if (!state.ready(document, runCatching { URI(shown).rawPath }.getOrNull())) return
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

    /**
     * The page's messages arrive on WebView's own thread, and only from our
     * server's page in [view], in the load the app asked for last: one from a
     * document the app has since replaced, or a web view since gone, is
     * dropped.
     */
    private inner class Bridge(private val view: WebView) {
        @JavascriptInterface
        fun postMessage(raw: String) {
            val message = SidebarPage.parse(raw) ?: return
            val document = state.document
            main.post {
                if (destroyed || view !== webView || document != state.document) return@post
                val shown = view.url
                if (shown != null && AppUrls.isAppUrl(shown)) receive(message, document, shown)
            }
        }
    }

    /** The page's load, or a reload of it, failed: the native sidebar meanwhile. */
    private fun failed(status: Int?) {
        val wasShowing = state.showing
        val missing = state.failed(status)
        apply()
        when {
            missing -> listener.onSidebarMissing()
            wasShowing -> listener.onSidebarFailed()
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
                        failed(null)
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

        // Also a reload once it drew (back from the background), which replaced the page with the error.
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (isCurrentLoad(view, request)) failed(response.statusCode)
        }

        // Only the page asked for last: an earlier one's error, arriving after another was asked for, isn't its.
        private fun isCurrentLoad(view: WebView, request: WebResourceRequest) =
            request.isForMainFrame && view === webView && state.isCurrent(request.url.encodedPath)

        // Its renderer gone (memory pressure, a crash): a new web view, and the page again as the drawer next opens.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            container.removeView(view)
            view.destroy()
            webView = makeWebView()
            container.addView(webView)
            state.rendererGone()
            apply()
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            // Offline, the worker's copy may still draw after this: `ready` then shows it.
            if (isCurrentLoad(view, request)) failed(null)
        }
    }
}
