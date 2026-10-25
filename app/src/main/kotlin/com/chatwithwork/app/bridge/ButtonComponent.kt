package com.chatwithwork.app.bridge

import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `button`: an action that clicks a link or button in the page. Same messages
 * as Joe Masilotti's component and the iOS app's (`left`, `right`,
 * `disconnect`), one button per page: a second one replaces the first.
 *
 * It's an action in the top app bar. Android has no left side for actions
 * (the navigation icon lives there), so a `left` button sits on the right
 * too. New chat (`nativeAction: "new-chat"`) is left out: every screen's
 * bar has the app's own New chat, which starts it in the sidebar's list.
 * That depends on the stable `nativeAction`, never on its title, which the
 * server may translate.
 *
 * `androidImage` is a Material Symbols name; a `.fill` suffix
 * ("keep.fill", like iOS's "pin.fill") draws the filled symbol.
 */
class ButtonComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_BUTTON

    private var button: Message? = null

    override fun onReceive(message: Message) {
        when (message.event) {
            "left", "right", "connect" -> {
                if (message.data<Data>() == null) return
                button = message
                redraw()
            }

            "disconnect" -> {
                button = null
                redraw()
            }
        }
    }

    override fun render(toolbar: Toolbar) {
        val message = button ?: return
        val data = message.data<Data>() ?: return
        if (data.nativeAction == NEW_CHAT) return

        toolbar.menu.add(menuGroup, ITEM_ID, ORDER_BUTTON, data.title).apply {
            val symbol = SymbolDrawable.from(toolbar.context, data.image)
            if (symbol != null) {
                icon = symbol
                tint(iconColor(toolbar, data.color))
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            } else {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            }
            describe(data.title)
            setOnMenuItemClickListener {
                replyWith(message)
                true
            }
        }
    }

    @Serializable
    data class Data(
        val title: String,
        @SerialName("androidImage") val image: String? = null,
        val color: String? = null,
        /** A stable name for the action (`new-chat`), for native code that needs one; never the title. */
        val nativeAction: String? = null
    )

    companion object {
        private const val ITEM_ID = 0x0C300

        /** A page's New chat, which the bar's own New chat stands for. */
        const val NEW_CHAT = "new-chat"

        val factory = BridgeComponentFactory("button", ::ButtonComponent)
    }
}
