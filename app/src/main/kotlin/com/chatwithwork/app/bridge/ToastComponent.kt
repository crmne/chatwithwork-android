package com.chatwithwork.app.bridge

import android.widget.Toast
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `toast`: a short message as a Material snackbar above the bottom bar, for
 * flash messages ("Connected Slack"). Joe Masilotti's `show` with `message`,
 * plus `type` as in the iOS app: `notice` (default), `info`, `alert`, or
 * `error`, the kinds of Rails flash. Alerts and errors stay up longer.
 */
class ToastComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        if (message.event != "show") return
        val data = message.data<Data>() ?: return
        if (data.message.isBlank()) return

        val activity = bridgeDelegate.destination.fragment.activity ?: return
        val host = activity as? SnackbarHost
        if (host != null) {
            host.showSnackbar(data.message, long = data.type == "alert" || data.type == "error")
        } else {
            Toast.makeText(activity, data.message, Toast.LENGTH_SHORT).show()
        }
    }

    @Serializable
    data class Data(val message: String, val type: String? = null)

    companion object {
        val factory = BridgeComponentFactory("toast", ::ToastComponent)
    }
}

/** Implemented by the activity that knows where snackbars belong. */
interface SnackbarHost {
    fun showSnackbar(message: String, long: Boolean = false)
}
