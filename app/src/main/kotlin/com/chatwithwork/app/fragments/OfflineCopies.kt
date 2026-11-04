package com.chatwithwork.app.fragments

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Chats kept for reading offline (the Rails app's docs/offline-chats.md).
 * The server's service worker keeps the chats someone opens and answers a
 * page with its copy when the network fails, so the app only has to let the
 * web view show it.
 *
 * Offline, a cold boot (a launch, or Hotwire Native's reload) first reports
 * the page's own request as failed (`ERROR_HOST_LOOKUP` and the like: the
 * worker's request to the network) and only then renders what the worker
 * answered, after the web view's own error page. Hotwire Native takes that
 * report as the visit failing: it resets the session and the app's offline
 * screen covers the copy, which is then never wired up.
 * [OfflineWebViewClient] holds a main frame's network error, and the error
 * page's finish, until the worker's answer finishes: a kept copy (marked
 * `<meta name="offline-copy">`) drops the error, so the cold boot goes on
 * with it; the worker's own offline page, or nothing within 3 seconds, gets
 * the error as before, and with it the app's offline screen.
 */
object OfflineCopies {
    /** Wraps the session's client once; Hotwire Native makes a new web view after a renderer crash. */
    fun install(webView: WebView) {
        val client = webView.webViewClient
        if (client !is OfflineWebViewClient) webView.webViewClient = OfflineWebViewClient(client)
    }

    /** Whether a failed request may be the network failing behind a page the worker still answers. */
    fun mayBeAnswered(isForMainFrame: Boolean, errorCode: Int): Boolean = isForMainFrame && errorCode in NETWORK_ERRORS

    /** What rendered after a held error, from [WHICH_PAGE]'s result. */
    enum class Page { COPY, OFFLINE, OTHER }

    fun page(result: String?): Page = when (result) {
        "\"copy\"" -> Page.COPY
        "\"offline\"" -> Page.OFFLINE
        else -> Page.OTHER
    }

    /**
     * A kept copy carries `<meta name="offline-copy">`; the worker's own
     * offline page (public/offline.html) has `main[data-offline]`. Anything
     * else is the web view's error page, or a page that isn't ours.
     */
    const val WHICH_PAGE =
        "document.querySelector('meta[name=\"offline-copy\"]') ? 'copy' : " +
            "document.querySelector('main[data-offline]') ? 'offline' : 'other'"

    private val NETWORK_ERRORS = setOf(
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT,
        WebViewClient.ERROR_IO,
        WebViewClient.ERROR_UNKNOWN
    )
}

/** Hotwire Native's own client, everything handed through but the error [OfflineCopies] describes. */
internal class OfflineWebViewClient(private val inner: WebViewClient) : WebViewClient() {
    private var held: Pair<WebResourceRequest, WebResourceError>? = null
    private val handler = Handler(Looper.getMainLooper())
    private var release: Runnable? = null

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!OfflineCopies.mayBeAnswered(request.isForMainFrame, error.errorCode)) {
            inner.onReceivedError(view, request, error)
            return
        }
        // Held until the page finishes, or a while if it never does.
        held = request to error
        release?.let(handler::removeCallbacks)
        release = Runnable { releaseHeld(view) }.also { handler.postDelayed(it, HOLD_MS) }
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (held == null) {
            inner.onPageFinished(view, url)
            return
        }
        view.evaluateJavascript(OfflineCopies.WHICH_PAGE) { result ->
            when (OfflineCopies.page(result)) {
                OfflineCopies.Page.COPY -> {
                    Log.i(TAG, "Showing a kept copy offline")
                    held = null
                    release?.let(handler::removeCallbacks)
                    release = null
                    inner.onPageFinished(view, url)
                }

                OfflineCopies.Page.OFFLINE -> {
                    releaseHeld(view)
                    inner.onPageFinished(view, url)
                }

                // The failed request's own error page commits first; the
                // worker's answer, if any, finishes after it. Until then
                // Hotwire Native mustn't take this page as the cold boot.
                OfflineCopies.Page.OTHER -> Unit
            }
        }
    }

    private fun releaseHeld(view: WebView) {
        release?.let(handler::removeCallbacks)
        release = null
        val (request, error) = held ?: return
        held = null
        inner.onReceivedError(view, request, error)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        inner.shouldOverrideUrlLoading(view, request)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = inner.onPageStarted(view, url, favicon)

    override fun onPageCommitVisible(view: WebView, url: String) = inner.onPageCommitVisible(view, url)

    override fun onLoadResource(view: WebView, url: String) = inner.onLoadResource(view, url)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        inner.shouldInterceptRequest(view, request)

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) =
        inner.onReceivedHttpError(view, request, errorResponse)

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) =
        inner.onReceivedSslError(view, handler, error)

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) =
        inner.onReceivedClientCertRequest(view, request)

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) =
        inner.onReceivedHttpAuthRequest(view, handler, host, realm)

    override fun onReceivedLoginRequest(view: WebView, realm: String, account: String?, args: String) =
        inner.onReceivedLoginRequest(view, realm, account, args)

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) =
        inner.onFormResubmission(view, dontResend, resend)

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) =
        inner.doUpdateVisitedHistory(view, url, isReload)

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) =
        inner.onScaleChanged(view, oldScale, newScale)

    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean =
        inner.shouldOverrideKeyEvent(view, event)

    override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) = inner.onUnhandledKeyEvent(view, event)

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean =
        inner.onRenderProcessGone(view, detail)

    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse
    ) = inner.onSafeBrowsingHit(view, request, threatType, callback)

    private companion object {
        const val TAG = "OfflineCopies"
        const val HOLD_MS = 3_000L
    }
}
