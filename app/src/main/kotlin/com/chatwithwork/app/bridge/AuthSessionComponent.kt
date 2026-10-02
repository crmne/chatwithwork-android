package com.chatwithwork.app.bridge

import android.net.Uri
import androidx.core.net.toUri
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.main.AuthCallbacks
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `auth-session`: runs a sign-in in an Auth Tab, Chrome's browser tab made
 * for sign-ins, instead of the web view. Google refuses OAuth inside an app's
 * web view, and passwords and passkeys saved in the browser only work there,
 * so signing in with Google, Slack, or Dropbox and connecting a service start
 * here. Where the browser has no Auth Tab, a Custom Tab runs it instead.
 *
 * The page asks the server for a one-time URL and sends it; the flow runs in
 * the browser, and the server ends it by redirecting to `chatwithwork://...`,
 * which the Auth Tab hands straight back (a Custom Tab, through the app's
 * intent filter). The page gets that callback URL and finishes in the web
 * view (docs/server-contract.md, "Browser sign-in"). Same contract as the
 * iOS app's component:
 *
 * - Web to native: `start` with `{url, ephemeral?}` (`url` must be on this
 *   app's server), and `cancel`.
 * - Native to web: a reply to `start` with `{url}` (the callback URL) or
 *   `{error}`: `canceled` (the person closed the tab), `invalid_url`, or
 *   `unavailable` (no browser can open it).
 */
class AuthSessionComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        when (message.event) {
            "start" -> start(message)
            "cancel" -> AuthCallbacks.cancel()
        }
    }

    private fun start(message: Message) {
        val request = message.data<Request>()
        val url = request?.url?.let(::resolve)
        if (url == null) {
            replyTo("start", Reply(error = "invalid_url"))
            return
        }

        val activity = bridgeDelegate.destination.fragment.activity ?: return
        val host = activity as? AuthTabHost ?: return
        AuthCallbacks.begin { callback -> replyTo("start", Reply(url = callback)) }
        (activity as? BrowserReturnHost)?.onNextReturn {
            // Back in the app without the callback: the person closed the tab.
            if (AuthCallbacks.cancel()) replyTo("start", Reply(error = "canceled"))
        }

        if (!host.launchAuthTab(url.toUri(), ephemeral = request.ephemeral == true)) {
            AuthCallbacks.cancel()
            replyTo("start", Reply(error = "unavailable"))
        }
    }

    /** Only this app's own server may start a session, over https outside development. */
    private fun resolve(url: String): String? {
        val absolute = if (url.startsWith("/")) AppConfig.baseUrl + url else url
        if (!AppUrls.isAppUrl(absolute)) return null
        if (!absolute.startsWith("https://") && AppConfig.environment != "development") return null
        return absolute
    }

    @Serializable
    data class Request(val url: String, val ephemeral: Boolean? = null)

    @Serializable
    data class Reply(val url: String? = null, val error: String? = null)

    companion object {
        val factory = BridgeComponentFactory("auth-session", ::AuthSessionComponent)
    }
}

/** Implemented by the activity, which owns the Auth Tab's result launcher. */
interface AuthTabHost {
    /** Opens [uri] for a sign-in that ends at chatwithwork://. False when no browser can. */
    fun launchAuthTab(uri: Uri, ephemeral: Boolean): Boolean
}

/** Implemented by the activity, which knows when the person comes back from the browser. */
interface BrowserReturnHost {
    fun onNextReturn(action: () -> Unit)
}
