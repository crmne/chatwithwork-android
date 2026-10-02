package com.chatwithwork.app.bridge

import android.graphics.drawable.Drawable
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
 * On a tab's first page (the chat list, the project list), the button is the
 * screen's primary action, so it becomes the floating action button above
 * the bottom bar ("New chat", "New project"), the way Android apps show it.
 * Everywhere else it's an action in the top app bar. Android has no left side
 * for actions (the navigation icon lives there), so a `left` button sits on
 * the right too. Where it goes depends on the page, never on its title,
 * which the server may translate.
 *
 * `androidImage` is a Material Symbols name; a `.fill` suffix
 * ("keep.fill", like iOS's "pin.fill") draws the filled symbol.
 */
class ButtonComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_BUTTON

    private var button: Message? = null
    private var started = true

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
        if (toolbar == null) publishFloatingAction()
    }

    override fun onStart() {
        started = true
        publishFloatingAction()
    }

    override fun onStop() {
        // A page that's no longer on screen gives up the floating button.
        started = false
        floatingActionHost?.setFloatingAction(destination.navigator.configuration.name, null)
    }

    override fun render(toolbar: Toolbar) {
        if (publishFloatingAction()) return
        val message = button ?: return
        val data = message.data<Data>() ?: return

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

    /**
     * Hands the button to the activity as the floating action when this page
     * is a tab's first page. Returns whether it did.
     */
    private fun publishFloatingAction(): Boolean {
        val host = floatingActionHost ?: return false
        val navigator = destination.navigator
        val name = navigator.configuration.name
        val isTabRoot = !destination.isModal && navigator.isAtStartDestination()
        val message = button
        val data = message?.data<Data>()

        if (!started || !isTabRoot || message == null || data == null) {
            if (started && isTabRoot) host.setFloatingAction(name, null)
            return false
        }

        val context = destination.fragment.context ?: return false
        host.setFloatingAction(
            name,
            FloatingAction(
                title = data.title,
                icon = SymbolDrawable.from(context, data.image),
                onClick = { replyWith(message) }
            )
        )
        return true
    }

    private val floatingActionHost: FloatingActionHost?
        get() = destination.fragment.activity as? FloatingActionHost

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

        val factory = BridgeComponentFactory("button", ::ButtonComponent)
    }
}

/** A screen's primary action, shown as the floating action button. */
class FloatingAction(val title: String, val icon: Drawable?, val onClick: () -> Unit)

/** Implemented by the activity, which owns the floating action button. */
interface FloatingActionHost {
    /** Sets (or with null, clears) the floating action for a tab's first page. */
    fun setFloatingAction(navigatorName: String, action: FloatingAction?)
}
