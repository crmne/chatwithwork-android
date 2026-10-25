package com.chatwithwork.app.bridge

import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination

/**
 * `sidebar`: the app draws the sidebar itself, and says so by registering
 * this component (the user agent's bridge-components then lists it, which
 * is how the server knows a sidebar build from a tab build). The page's
 * hidden `bridge--sidebar` element listens to the person's chat lists and
 * sends:
 *
 * - `connect` `{}`: nothing to do.
 * - `changed` `{}`: a chat started, was renamed, answered, waits, or was
 *   pinned. The app fetches the sidebar again (debounced) when it's open,
 *   and otherwise the next time it opens.
 *
 * See docs/server-contract.md, "The sidebar".
 */
class SidebarComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        if (message.event == "changed") {
            (bridgeDelegate.destination.fragment.activity as? SidebarHost)?.onSidebarChanged()
        }
    }

    companion object {
        val factory = BridgeComponentFactory("sidebar", ::SidebarComponent)
    }
}

/** Implemented by the activity, which owns the sidebar. */
interface SidebarHost {
    fun onSidebarChanged()
}
