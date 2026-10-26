package com.chatwithwork.app.bridge

import com.chatwithwork.app.sidebar.Sidebar
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
 * - `connect` `{}`: the app keeps it, to answer with a chat's action from
 *   the sidebar's long press: `{action: "perform", url, method, confirm?,
 *   destructive?}`, which the page sends as the web row's menu would.
 * - `changed` `{}`: a chat started, was renamed, answered, waits, or was
 *   pinned. The app fetches the sidebar again (debounced) when it's open,
 *   and otherwise the next time it opens.
 *
 * See docs/server-contract.md, "The sidebar".
 */
class SidebarComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> connected.add(WeakReference(this))
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
}
