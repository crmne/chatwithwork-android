package com.chatwithwork.app.fragments

import android.content.ContextWrapper
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A form posted as a whole page that comes back with its mistakes stays the page (a wrong password). */
class FormRerendersTest {
    @Test
    fun `a form posted and shown again with 422 is the page, not an error`() {
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 422)).isTrue()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "PUT", status = 422)).isTrue()
    }

    @Test
    fun `anything else stays an error`() {
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "GET", status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = null, status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = false, method = "POST", status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 500)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 401)).isFalse()
    }
}

/** The wrapper itself: the 422 stays the page, and the next navigation Hotwire Native proposes starts afresh. */
class FormRerenderClientTest {
    private class Request(private val mainFrame: Boolean, private val method: String) : WebResourceRequest {
        override fun getUrl(): Uri? = null
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = method
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private class Response(private val status: Int) : WebResourceResponse("text/html", "utf-8", null) {
        override fun getStatusCode() = status
    }

    private class Inner(var overrides: Boolean = true) : WebViewClient() {
        val calls = mutableListOf<String>()

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            calls += "error ${errorResponse.statusCode}"
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            calls += "override"
            return overrides
        }
    }

    private val view = WebView(ContextWrapper(null))
    private val inner = Inner()
    private val resets = mutableListOf<String>()
    private val client = OfflineWebViewClient(inner) {
        resets += "reset after ${inner.calls.lastOrNull()}"
    }

    @Test
    fun `a form shown again stays the page, and the next proposed visit resets the session once, after Hotwire`() {
        client.onReceivedHttpError(view, Request(mainFrame = true, method = "POST"), Response(422))
        assertThat(inner.calls).isEmpty()

        assertThat(client.shouldOverrideUrlLoading(view, Request(mainFrame = true, method = "GET"))).isTrue()
        assertThat(resets).containsExactly("reset after override")

        client.shouldOverrideUrlLoading(view, Request(mainFrame = true, method = "GET"))
        assertThat(resets).hasSize(1)
    }

    @Test
    fun `other errors still reach Hotwire Native, and nothing resets without a form shown again`() {
        client.onReceivedHttpError(view, Request(mainFrame = true, method = "GET"), Response(422))
        client.onReceivedHttpError(view, Request(mainFrame = true, method = "POST"), Response(500))
        client.onReceivedHttpError(view, Request(mainFrame = false, method = "POST"), Response(422))
        assertThat(inner.calls).containsExactly("error 422", "error 500", "error 422").inOrder()

        client.shouldOverrideUrlLoading(view, Request(mainFrame = true, method = "GET"))
        assertThat(resets).isEmpty()
    }

    @Test
    fun `a navigation Hotwire Native leaves to the web view doesn't reset`() {
        client.onReceivedHttpError(view, Request(mainFrame = true, method = "POST"), Response(422))
        inner.overrides = false
        assertThat(client.shouldOverrideUrlLoading(view, Request(mainFrame = true, method = "GET"))).isFalse()
        assertThat(resets).isEmpty()
    }
}
