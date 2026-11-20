package com.chatwithwork.app.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.chatwithwork.app.AppConfig
import java.lang.ref.WeakReference

/**
 * Tells the pages what the network costs (docs/server-contract.md, "Pages
 * loaded ahead"): `window.chatWithWorkNetwork = {metered, constrained}`,
 * which the page reads before loading Decisions, New chat and New bot ahead.
 * `metered` is a network without NOT_METERED (mobile data, a hotspot),
 * `constrained` Data Saver on for this app. It's set before the page's own
 * scripts in every document (a document-start script, replaced on each
 * change), and in the pages already open when it changes, which then preload
 * if the network now costs nothing. Nothing of it goes to the server.
 */
object NetworkCost {
    data class Value(val metered: Boolean, val constrained: Boolean) {
        val free: Boolean get() = !metered && !constrained
    }

    /** What a network's capabilities and Data Saver's state say; null with no network. */
    fun value(notMetered: Boolean?, restrictBackgroundStatus: Int): Value? = notMetered?.let {
        Value(
            metered = !it,
            constrained = restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        )
    }

    /** Sets the value, and when [preload] asks the page to load its places ahead. */
    fun script(value: Value, preload: Boolean): String =
        "window.chatWithWorkNetwork = { metered: ${value.metered}, constrained: ${value.constrained} };" +
            if (preload) " window.chatWithWork && window.chatWithWork.preload && window.chatWithWork.preload();" else ""

    private var current: Value? = null
    private var started = false
    private val main = Handler(Looper.getMainLooper())
    private val webViews = mutableListOf<Pair<WeakReference<WebView>, ScriptHandler?>>()

    /** Follows the default network and Data Saver for the app's life. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return
        fun read(capabilities: NetworkCapabilities?) {
            val notMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val value = value(notMetered, connectivity.restrictBackgroundStatus)
            main.post { changed(value) }
        }
        read(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    read(capabilities)

                override fun onLost(network: Network) = read(null)
            }
        )
        // Data Saver turned on or off, a system broadcast only registered receivers hear.
        val dataSaver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) =
                    read(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
            }
        val filter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(dataSaver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(dataSaver, filter)
        }
    }

    /** A web view the app shows pages in: every document it loads starts knowing the value. */
    fun install(webView: WebView) {
        webViews.removeAll { it.first.get() == null }
        webViews += WeakReference(webView) to documentStart(webView, current)
    }

    /** A page that loaded before the value could be set at its start (a web view without that feature). */
    fun apply(webView: WebView) {
        current?.let { webView.evaluateJavascript(script(it, preload = false), null) }
    }

    private fun changed(value: Value?) {
        if (value == current) return
        current = value
        webViews.removeAll { it.first.get() == null }
        webViews.replaceAll { (reference, handler) ->
            handler?.remove()
            val webView = reference.get() ?: return@replaceAll reference to null
            if (value != null) webView.evaluateJavascript(script(value, preload = value.free), null)
            reference to documentStart(webView, value)
        }
    }

    private fun documentStart(webView: WebView, value: Value?): ScriptHandler? {
        if (value == null || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
        return WebViewCompat.addDocumentStartJavaScript(
            webView,
            script(value, preload = false),
            setOf(AppConfig.baseUrl)
        )
    }
}
