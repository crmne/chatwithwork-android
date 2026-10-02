package com.chatwithwork.app.bridge

import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `form`: the page's submit button as a text action in the top app bar
 * ("Save"), disabled while the form submits. Same messages as Joe Masilotti's
 * component: `connect`, `enableSubmit`, `disableSubmit`, `disconnect`.
 */
class FormComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_FORM

    private var title: String? = null
    private var enabled = true

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                title = message.data<Data>()?.title
                enabled = true
                redraw()
            }

            "enableSubmit" -> {
                enabled = true
                redraw()
            }

            "disableSubmit" -> {
                enabled = false
                redraw()
            }

            "disconnect" -> {
                title = null
                clear()
            }
        }
    }

    override fun render(toolbar: Toolbar) {
        val title = title ?: return

        toolbar.menu.add(menuGroup, ITEM_ID, ORDER_FORM, title).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            isEnabled = enabled
            describe(title)
            setOnMenuItemClickListener {
                replyTo("connect")
                true
            }
        }
    }

    @Serializable
    data class Data(val title: String, val color: String? = null)

    companion object {
        private const val ITEM_ID = 0x0C200

        val factory = BridgeComponentFactory("form", ::FormComponent)
    }
}
