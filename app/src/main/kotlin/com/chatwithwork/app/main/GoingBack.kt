package com.chatwithwork.app.main

/**
 * What the system back does, as Google's navigation principles have it for
 * a drawer's top-level destinations (docs/server-contract.md, section 2):
 * the sidebar closes first; in a drill-down or a modal it pops one step
 * (Hotwire Native, and MainActivity's own modal callback); at a place other
 * than the home it goes to the home, Decisions, the start destination; and
 * at the home it leaves the app, as the system does. A place opened from a
 * notification or a link behaves the same, as if the person had come from
 * the home. There is no history of places. Only [Step.GoHome] is the app's
 * own, so its callback is enabled only then, and the predictive back
 * gesture shows the system's own animation on the way out.
 */
object GoingBack {
    sealed interface Step {
        data object CloseSidebar : Step

        data object Pop : Step

        data class GoHome(val location: String) : Step

        data object Leave : Step
    }

    fun step(sidebarOpen: Boolean, depth: Int, atHome: Boolean, home: String?): Step = when {
        sidebarOpen -> Step.CloseSidebar
        depth > 1 -> Step.Pop
        !atHome && home != null -> Step.GoHome(home)
        else -> Step.Leave
    }
}
