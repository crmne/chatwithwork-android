package com.chatwithwork.app.fragments

import android.webkit.PermissionRequest
import android.webkit.WebView
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.turbo.session.Session
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient

/**
 * Hotwire Native's chrome client (file chooser, dialogs, new windows), with
 * the microphone only for the app's own pages: the composer's dictation
 * records with getUserMedia. Hotwire Native asks Android for RECORD_AUDIO
 * when it isn't granted yet, then grants the request. Anything else, the
 * camera or a page from another site (an MCP App's frame), is denied.
 *
 * It also passes on every change of the page's `<title>` to [onTitle]:
 * Hotwire Native reads the title only when a visit renders, and a chat
 * retitles itself when its automatic title arrives.
 */
class AppWebChromeClient(session: Session, private val onTitle: (String) -> Unit = {}) :
    HotwireWebChromeClient(session) {
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        PageTitles.live(title, view?.url)?.let(onTitle)
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
