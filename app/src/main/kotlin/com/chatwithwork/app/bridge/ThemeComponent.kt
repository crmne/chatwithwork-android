package com.chatwithwork.app.bridge

import com.chatwithwork.app.AppTheme
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `theme`: lets the page choose light or dark for the whole app, native bars
 * included. Same message as Joe Masilotti's component: `connect` with
 * `theme` set to "light", "dark", or nothing to follow the system. The choice
 * is remembered, so the next launch starts in it instead of flashing the
 * system theme first.
 *
 * Chat with Work follows the system today and doesn't send this; it's
 * registered so a future appearance setting needs no app update.
 */
class ThemeComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        if (message.event != "connect") return
        val context = bridgeDelegate.destination.fragment.context ?: return
        val mode = AppTheme.Mode.fromWeb(message.data<Data>()?.theme)
        AppTheme.apply(context, mode)
    }

    @Serializable
    data class Data(val theme: String? = null)

    companion object {
        val factory = BridgeComponentFactory("theme", ::ThemeComponent)
    }
}
