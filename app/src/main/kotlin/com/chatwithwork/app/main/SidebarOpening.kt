package com.chatwithwork.app.main

/**
 * The page the sidebar is opening. The next screen takes it, so only that
 * screen keeps the menu button: the same page reached later by a link, a
 * notification or from another page gets back, as any pushed page does.
 */
class SidebarOpening {
    private var location: String? = null

    fun opening(location: String) {
        this.location = location
    }

    fun take(location: String): Boolean {
        val opened = this.location == location
        this.location = null
        return opened
    }
}
