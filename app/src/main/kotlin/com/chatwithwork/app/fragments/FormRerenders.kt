package com.chatwithwork.app.fragments

/**
 * The server's sign-in, sign-up and password forms post as whole pages, not
 * through Turbo (`data-turbo="false"`), and a form with a mistake in it (a
 * wrong password) comes back as itself with 422, its error under the field.
 * Hotwire Native takes any main frame's HTTP error as the visit failing: it
 * resets the session and the app's error screen covers the form.
 * [OfflineWebViewClient] keeps such an answer as the page instead, and as the
 * page has no Turbo bridge, the next navigation from it (the form posted
 * again and redirected, or a link) starts the session afresh.
 */
object FormRerenders {
    /** Whether a main frame's HTTP error is a form shown again with its mistakes. */
    fun isRerender(isForMainFrame: Boolean, method: String?, status: Int): Boolean =
        isForMainFrame && status == UNPROCESSABLE && method != null && !method.equals("GET", ignoreCase = true)

    private const val UNPROCESSABLE = 422
}
