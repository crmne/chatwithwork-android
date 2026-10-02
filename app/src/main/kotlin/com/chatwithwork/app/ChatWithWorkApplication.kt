package com.chatwithwork.app

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import com.chatwithwork.app.bridge.BridgeComponents
import com.chatwithwork.app.fragments.WebFragment
import com.chatwithwork.app.fragments.WebModalFragment
import com.chatwithwork.app.push.PushNotifications
import com.chatwithwork.app.routing.RouteDecisionHandlers
import dev.hotwire.core.bridge.KotlinXJsonConverter
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.logging.HotwireLogLevel
import dev.hotwire.core.turbo.config.PathConfiguration
import dev.hotwire.core.turbo.webview.HotwireWebView
import dev.hotwire.navigation.config.defaultFragmentDestination
import dev.hotwire.navigation.config.registerBridgeComponents
import dev.hotwire.navigation.config.registerFragmentDestinations
import dev.hotwire.navigation.config.registerRouteDecisionHandlers

class ChatWithWorkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppTheme.applySaved(this)
        CookieManager.getInstance().setAcceptCookie(true)
        configureHotwire()
        PushNotifications.initialize(this)
    }

    private fun configureHotwire() {
        // Bridge messages are decoded with kotlinx.serialization; without a
        // converter, the first message crashes the app.
        Hotwire.config.jsonConverter = KotlinXJsonConverter()
        Hotwire.config.applicationUserAgentPrefix = AppConfig.userAgentPrefix(this)
        Hotwire.config.webViewDebuggingEnabled = BuildConfig.DEBUG
        Hotwire.config.logger.logLevel = if (BuildConfig.DEBUG) HotwireLogLevel.DEBUG else HotwireLogLevel.NONE
        Hotwire.config.makeCustomWebView = ::makeWebView

        Hotwire.defaultFragmentDestination = WebFragment::class
        // Modals are full-screen fragments, not bottom sheets: the bridge
        // components that cast their destination to HotwireFragment (Joe
        // Masilotti's search, for one) crash in Hotwire's sheet fragments.
        Hotwire.registerFragmentDestinations(
            WebFragment::class,
            WebModalFragment::class
        )
        Hotwire.registerBridgeComponents(*BridgeComponents.all)
        Hotwire.registerRouteDecisionHandlers(*RouteDecisionHandlers.all)

        // The bundled rules work offline and on first launch; the server's
        // copy replaces them once it loads (and is cached for next time).
        Hotwire.loadPathConfiguration(
            context = this,
            location =
                PathConfiguration.Location(
                    assetFilePath = AppConfig.BUNDLED_PATH_CONFIGURATION,
                    remoteFileUrl = AppConfig.remotePathConfigurationUrl
                )
        )
    }

    /**
     * No white flash before a page paints: the web view starts on the canvas
     * color. No scroll bars either: native screens don't show them, and a
     * page that's a little too wide would otherwise keep a bar along the
     * bottom edge.
     */
    private fun makeWebView(context: Context): HotwireWebView = HotwireWebView(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.lw_canvas))
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }
}
