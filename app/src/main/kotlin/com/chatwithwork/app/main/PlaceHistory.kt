package com.chatwithwork.app.main

import java.net.URI

/**
 * The places the person was at, newest first, so the system back at a place
 * goes back to the one before, as a browser's does (docs/server-contract.md,
 * section 2, "The layout in the apps"). It's the app's own short list: going back
 * never adds to it (A, then B, back to A, back goes on to what was before A,
 * never B again); a place already on it moves to the top rather than
 * showing twice; the place on screen is never on it; and back reaching the
 * home clears it, so back at the home always leaves the app.
 */
class PlaceHistory(private val cap: Int = CAP) {
    private val stops = ArrayDeque<String>()

    /** The place on screen, as the last arrival said. */
    var current: String? = null
        private set

    /** The place back is opening, until it arrives or fails. */
    var returningTo: String? = null
        private set

    /** The last place back reached, which may still fail to load. */
    private var returned: String? = null

    val entries: List<String> get() = stops.toList()

    /** A place, [location], shows as the root; [atHome] when it's the person's home. */
    fun arrived(location: String, atHome: Boolean) {
        val back = returningTo != null
        returningTo = null
        returned = location.takeIf { back }
        val previous = current
        current = location
        stops.removeAll { same(it, location) }
        if (back) {
            if (atHome) stops.clear()
            return
        }
        if (previous == null || same(previous, location)) return
        stops.addFirst(previous)
        while (stops.size > cap) stops.removeLast()
    }

    /** The place back would open: the newest one [worth] returning to (an empty New chat isn't), or none. */
    fun next(worth: (String) -> Boolean = { true }): String? = stops.firstOrNull { !same(it, current) && worth(it) }

    /** Back opens [location] (from [next], or the home): it and every stop above it leave the list. */
    fun returnTo(location: String) {
        val index = stops.indexOfFirst { same(it, location) }
        if (index >= 0) repeat(index + 1) { stops.removeFirst() }
        returningTo = location
    }

    /**
     * [location] failed to load (gone, or no longer the person's). True when
     * back had opened it, so back goes on to the next place, or the home.
     */
    fun failed(location: String): Boolean {
        val wasReturn = same(location, returningTo) || same(location, returned)
        stops.removeAll { same(it, location) }
        if (!wasReturn) return false
        returningTo = null
        returned = null
        current = location
        return true
    }

    /** A new session or organization: nothing to go back to. */
    fun clear() {
        stops.clear()
        current = null
        returningTo = null
        returned = null
    }

    /** What outlives the process (the place on screen comes back as the navigator's own start). */
    fun saved(): ArrayList<String> = ArrayList(stops)

    fun restore(saved: List<String>?) {
        if (saved == null || stops.isNotEmpty()) return
        saved.take(cap).forEach(stops::addLast)
    }

    companion object {
        const val CAP = 5

        /**
         * Query keys a page changes as it's used, not where it is: Decisions
         * puts its view and the decision in focus in its address.
         */
        private val VOLATILE = setOf("decision", "view", "after")

        /** The same place: path and query, but for what a page changes in its own address. */
        fun same(first: String?, second: String?): Boolean =
            first != null && second != null && key(first) == key(second)

        fun key(url: String): String {
            val uri = runCatching { URI(url) }.getOrNull() ?: return url
            val path = uri.rawPath.orEmpty().trimEnd('/').ifEmpty { "/" }
            val query =
                uri.rawQuery.orEmpty().split("&").filter {
                    it.isNotEmpty() && it.substringBefore("=") !in VOLATILE
                }
            return if (query.isEmpty()) path else "$path?${query.joinToString("&")}"
        }
    }
}

/**
 * What the system back does, as a pure decision: the sidebar closes first;
 * in a drill-down or a modal it pops one step (Hotwire Native, and
 * MainActivity's own modal callback); at a place it goes back to the place
 * before ([previous], PlaceHistory), else to the [home] unless that's where
 * it is ([atHome]), else it leaves the app, as the system does. Only
 * [Step.GoTo] is the app's own, so its callback is enabled only then, and
 * the predictive back gesture shows the system's own animation otherwise.
 */
object GoingBack {
    sealed interface Step {
        data object CloseSidebar : Step

        data object Pop : Step

        data class GoTo(val location: String) : Step

        data object Leave : Step
    }

    fun step(sidebarOpen: Boolean, depth: Int, previous: String?, atHome: Boolean, home: String?): Step = when {
        sidebarOpen -> Step.CloseSidebar
        depth > 1 -> Step.Pop
        previous != null -> Step.GoTo(previous)
        !atHome && home != null -> Step.GoTo(home)
        else -> Step.Leave
    }
}
