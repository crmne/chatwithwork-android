package com.chatwithwork.app.bridge

import androidx.appcompat.R as AppCompatR
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `alert`: a Material dialog asking to confirm before the page goes ahead
 * (disconnecting a service, deleting a chat). Same message as Joe Masilotti's
 * component: `show`, answered only when the person confirms. A destructive
 * confirmation is drawn in the error color.
 *
 * Plain `data-turbo-confirm` and `confirm()` also become Material dialogs
 * without any of this (Hotwire Native's web chrome client handles them); this
 * component adds the title, the button labels, and the destructive style.
 */
class AlertComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        if (message.event != "show") return
        val data = message.data<Data>() ?: return
        val context = bridgeDelegate.destination.fragment.context ?: return

        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(data.title)
                .setMessage(data.description)
                .setNegativeButton(data.dismiss ?: context.getString(android.R.string.cancel), null)
                .setPositiveButton(data.confirm ?: context.getString(android.R.string.ok)) { _, _ ->
                    replyTo("show")
                }.show()

        if (data.destructive == true) {
            val error = MaterialColors.getColor(context, AppCompatR.attr.colorError, 0)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(error)
        }
    }

    @Serializable
    data class Data(
        val title: String,
        val description: String? = null,
        val destructive: Boolean? = null,
        val confirm: String? = null,
        val dismiss: String? = null
    )

    companion object {
        val factory = BridgeComponentFactory("alert", ::AlertComponent)
    }
}
