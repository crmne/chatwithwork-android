package com.chatwithwork.app.routing

import androidx.core.net.toUri
import dev.hotwire.core.turbo.visit.VisitProposal
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.routing.Router

/**
 * Decides what happens to a link before Hotwire Native navigates to it. The
 * handlers run in order and the first that matches decides.
 */
object RouteDecisionHandlers {
    val all: Array<Router.RouteDecisionHandler> =
        arrayOf(
            AppPages(),
            OtherSites(),
            OtherApps()
        )

    /** Path configuration property: open this page of ours in a browser tab instead. */
    const val OPEN_IN_BROWSER = "open_in_browser"

    /**
     * Implemented by the activity, which knows about tabs and signing in and
     * can take a link over (switch tabs, finish signing in) instead of
     * pushing it. It decides first; null leaves the decision to the page's
     * path configuration.
     */
    interface Host {
        fun routeDecision(proposal: VisitProposal, configuration: NavigatorConfiguration): Router.Decision?
    }

    /** Our own pages: navigate in the app, unless the page or the activity says otherwise. */
    class AppPages : Router.RouteDecisionHandler {
        override val name = "app-pages"

        override fun matches(proposal: VisitProposal, configuration: NavigatorConfiguration): Boolean =
            AppUrls.isAppUrl(proposal.location)

        override fun handle(
            proposal: VisitProposal,
            configuration: NavigatorConfiguration,
            activity: HotwireActivity
        ): Router.Decision {
            (activity as? Host)?.routeDecision(proposal, configuration)?.let { return it }

            if (proposal.properties[OPEN_IN_BROWSER] == true) {
                BrowserTabs.open(activity, proposal.location.toUri())
                return Router.Decision.CANCEL
            }

            return Router.Decision.NAVIGATE
        }
    }

    /** Other websites open in a browser tab over the app. */
    class OtherSites : Router.RouteDecisionHandler {
        override val name = "other-sites"

        override fun matches(proposal: VisitProposal, configuration: NavigatorConfiguration): Boolean {
            val scheme =
                proposal.location
                    .toUri()
                    .scheme
                    ?.lowercase()
            return scheme == "https" || scheme == "http"
        }

        override fun handle(
            proposal: VisitProposal,
            configuration: NavigatorConfiguration,
            activity: HotwireActivity
        ): Router.Decision {
            BrowserTabs.open(activity, proposal.location.toUri())
            return Router.Decision.CANCEL
        }
    }

    /**
     * mailto:, tel:, and a few other everyday schemes go to the app that
     * handles them. Anything else (intent:, file:, javascript:) is dropped:
     * pages shouldn't be able to start arbitrary parts of other apps.
     */
    class OtherApps : Router.RouteDecisionHandler {
        override val name = "other-apps"

        override fun matches(proposal: VisitProposal, configuration: NavigatorConfiguration): Boolean = true

        override fun handle(
            proposal: VisitProposal,
            configuration: NavigatorConfiguration,
            activity: HotwireActivity
        ): Router.Decision {
            val uri = proposal.location.toUri()
            if (uri.scheme?.lowercase() in SYSTEM_SCHEMES) {
                BrowserTabs.openWithSystem(activity, uri)
            }
            return Router.Decision.CANCEL
        }

        companion object {
            val SYSTEM_SCHEMES = setOf("mailto", "tel", "sms", "geo", "market")
        }
    }
}
