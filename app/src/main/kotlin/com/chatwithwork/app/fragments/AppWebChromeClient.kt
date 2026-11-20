package com.chatwithwork.app.fragments

import android.content.Context
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebView
import com.chatwithwork.app.bridge.ComposerHost
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.turbo.session.Session
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient

/**
 * Hotwire Native's chrome client (dialogs, new windows), with the app's own
 * file chooser for its pages (WebFileChooser), and
 * the microphone only for the app's own pages: the composer's dictation
 * records with getUserMedia. Hotwire Native asks Android for RECORD_AUDIO
 * when it isn't granted yet, then grants the request. Anything else, the
 * camera or a page from another site (an MCP App's frame), is denied.
 *
 * It also passes on every change of the page's `<title>` to [onTitle]:
 * Hotwire Native reads the title only when a visit renders, and a chat
 * retitles itself when its automatic title arrives.
 */
class AppWebChromeClient(
    session: Session,
    private val host: () -> ComposerHost? = { null },
    private val onTitle: (String) -> Unit = {}
) : HotwireWebChromeClient(session) {
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        PageTitles.live(title, view?.url)?.let(onTitle)
    }

    /**
     * The page's file inputs on this app's own pages pick from the camera,
     * photos, files, or the scanner (WebFileChooser); anywhere else, or for
     * an input narrower than pictures, Hotwire Native's chooser.
     */
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        val host = host()
        if (host != null && AppUrls.isAppUrl(webView.url.orEmpty()) &&
            WebFileChooser.show(host as? Context ?: webView.context, host, fileChooserParams, filePathCallback)
        ) {
            return true
        }
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        if (MediaCapture.allows(request.origin?.toString(), request.resources?.toList().orEmpty())) {
            super.onPermissionRequest(request)
        } else {
            request.deny()
        }
    }
}

object MediaCapture {
    /** Whether a page at [origin] may have [resources]: the microphone alone, on this app's server. */
    fun allows(origin: String?, resources: List<String>): Boolean = origin != null &&
        AppUrls.isAppUrl(origin) &&
        resources.isNotEmpty() &&
        resources.all { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
}
