package com.chatwithwork.app.sidebar

/**
 * Which load of the sidebar page its web view holds, and whether it shows in
 * place of the native sidebar (SidebarWebView), kept apart from Android so
 * it's tested on its own. See docs/server-contract.md, "The sidebar page",
 * "When it can't load".
 */
class SidebarPageState {
    /** What the web view does after a change. */
    enum class Change {
        NONE,

        /** Load [path]. */
        LOAD,

        /** Load nothing (about:blank): the native sidebar it is. */
        CLEAR
    }

    /** The page's path ("/482139075/sidebar"), once one was asked for. */
    var path: String? = null
        private set

    /** Whether the document now in the web view drew, and so shows in place of the native sidebar. */
    var showing = false
        private set

    /** Whether the latest load failed, to try again as the drawer next opens. */
    var failed = false
        private set

    /**
     * The load the app asked for last. A message is read with the number it
     * arrived under, so one from a document the app has since replaced or
     * dropped is refused. Read on WebView's own thread.
     */
    @Volatile
    var document = 0
        private set

    // Pages that answered 404, for the activity's life: a connect that names one again doesn't ask for it again.
    private val missing = mutableSetOf<String>()

    /** Shows the page at [path], loading it unless it's the one already there; null goes back to the native sidebar. */
    fun show(path: String?): Change {
        if (path == null || path in missing) return clear()
        if (path == this.path && !failed) return Change.NONE
        this.path = path
        showing = false
        failed = false
        document++
        return Change.LOAD
    }

    /** Forgets everything, the pages that were missing too (signed out, or signed in as someone else). */
    fun forget(): Change {
        missing.clear()
        return clear()
    }

    private fun clear(): Change {
        val had = path != null || showing || failed
        path = null
        showing = false
        failed = false
        if (!had) return Change.NONE
        document++
        return Change.CLEAR
    }

    /** The drawer opening: whether a page that couldn't load loads again. */
    fun retry(): Boolean {
        if (path == null || !failed) return false
        failed = false
        document++
        return true
    }

    /** The page's `ready`, from [document], the web view showing [shownPath]: whether it now shows. */
    fun ready(document: Int, shownPath: String?): Boolean {
        val path = path ?: return false
        if (document != this.document || shownPath != path) return false
        showing = true
        failed = false
        return true
    }

    /**
     * The page's load, or a reload of it, failed with [status] (null without
     * an answer: offline, or sent to the sign-in form): the native sidebar
     * meanwhile. Whether the server has no such page (404), which isn't
     * asked for again.
     */
    fun failed(status: Int?): Boolean {
        val path = path ?: return false
        showing = false
        failed = true
        if (status != HTTP_NOT_FOUND) return false
        missing += path
        return true
    }

    /** Its web view's renderer gone: a new web view, and the page again as the drawer next opens. */
    fun rendererGone() {
        showing = false
        failed = path != null
        document++
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
