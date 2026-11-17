package com.chatwithwork.app.bridge

import com.chatwithwork.app.sidebar.Sidebar
import com.chatwithwork.app.sidebar.SidebarPage
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import java.lang.ref.WeakReference
import kotlinx.serialization.Serializable

/**
 * `sidebar`: the app draws the sidebar itself, and says so by registering
 * this component (the user agent's bridge-components then lists it, which
 * is how the server knows a sidebar build from a tab build). The page's
 * hidden `bridge--sidebar` element listens to the person's chat lists and
 * sends:
 *
 * - `connect` `{page?, belongsTo?}`: the server's sidebar page for this
 *   organization, which the app then shows in the drawer (SidebarWebView,
 *   "The sidebar page"; an older server sends `{}`, and the app draws the
 *   JSON), and the path the page on screen belongs under, which the sidebar
 *   page marks. The app keeps the component, to answer with a chat's action
 *   from the native sidebar's long press: `{action: "perform", url, method,
 *   confirm?, destructive?}`, which the page sends as the web row's menu would.
 * - `changed` `{}`: a chat started, was renamed, answered, waits, or was
 *   pinned. The app fetches the sidebar again (debounced) when it's open,
 *   and otherwise the next time it opens. The sidebar page follows its own
 *   streams instead, so the app ignores it while that shows.
 *
 * See docs/server-contract.md, "The sidebar".
 */
class SidebarComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                connected.add(WeakReference(this))
                val connect = message.data<SidebarPage.Connect>() ?: SidebarPage.Connect()
                (bridgeDelegate.destination.fragment.activity as? SidebarHost)
                    ?.onSidebarConnect(connect, message.metadata?.url ?: bridgeDelegate.destination.location)
            }

            "changed" -> (bridgeDelegate.destination.fragment.activity as? SidebarHost)?.onSidebarChanged()
        }
    }

    @Serializable
    private data class Perform(
        val action: String = "perform",
        val url: String,
        val method: String,
        val confirm: String? = null,
        val destructive: Boolean = false
    )

    companion object {
        val factory = BridgeComponentFactory("sidebar", ::SidebarComponent)

        private val connected = mutableListOf<WeakReference<SidebarComponent>>()

        /**
         * Hands [action] to the page showing in [destination], or, failing
         * that, the last page that connected. False when no page can send it.
         */
        fun perform(action: Sidebar.Action, destination: HotwireDestination?): Boolean {
            connected.removeAll { it.get() == null }
            val components = connected.mapNotNull { it.get() }
            val component =
                components.lastOrNull { it.bridgeDelegate.destination === destination } ?: components.lastOrNull()
                    ?: return false
            return component.replyTo(
                "connect",
                Perform(
                    url = action.url,
                    method = action.method,
                    confirm = action.confirm,
                    destructive = action.destructive
                )
            )
        }
    }
}

/** Implemented by the activity, which owns the sidebar. */
interface SidebarHost {
    fun onSidebarChanged()

    /** A page's `connect`: the sidebar page it names (none from an older server), on the page at [location]. */
    fun onSidebarConnect(connect: SidebarPage.Connect, location: String)
}
