package com.chatwithwork.app.fragments

import android.webkit.PermissionRequest
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.turbo.session.Session
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient

/**
 * Hotwire Native's chrome client (file chooser, dialogs, new windows), with
 * the microphone only for the app's own pages: the composer's dictation
 * records with getUserMedia. Hotwire Native asks Android for RECORD_AUDIO
 * when it isn't granted yet, then grants the request. Anything else, the
 * camera or a page from another site (an MCP App's frame), is denied.
 */
class AppWebChromeClient(session: Session) : HotwireWebChromeClient(session) {
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
