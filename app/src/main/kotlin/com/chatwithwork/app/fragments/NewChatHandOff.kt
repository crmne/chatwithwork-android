package com.chatwithwork.app.fragments

import android.os.SystemClock
import com.chatwithwork.app.composer.ComposerState
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.nav.Presentation

/**
 * New chat turning into its chat in place (path configuration v4; the
 * server's docs/ui/chat.md, "Sending a question"). The app's composer sends
 * through the page's hidden form, the page draws the question and Thinking in
 * the shared web view at once, and the server's redirect to `/chats/<n>` comes
 * back as a visit proposed with the form's response. That visit replaces the
 * screen the question was sent from (New chat, New bot's sentence, the home or
 * Decisions), in its place in the stack, with no animation, no skeleton and no
 * loading view over the web view that already shows the question, and renders
 * the response it carries rather than asking again. Back from the chat goes
 * where New chat was opened from.
 */
object NewChatHandOff {
    /**
     * Whether the visit proposed to [proposed], from the screen showing
     * [current], is the turn into the chat: proposed from a form's response
     * ([fromResponse]), to a chat that's a place (no `from=`), while the screen
     * shows a page whose composer turns into the chat.
     */
    fun turnsInPlace(current: String, proposed: String, fromResponse: Boolean): Boolean =
        fromResponse && AppUrls.turnsIntoChat(current) && AppUrls.isChatPlace(proposed) && AppUrls.isAppUrl(proposed)

    /** The chat about to replace the screen, and when it was handed over (uptime). */
    private var expected: String? = null
    private var expectedAt = 0L

    /**
     * The composer as the screen giving way last drew it (answering, with its
     * Stop), and whether its field had the keyboard up (one the person put
     * away stays away): the chat's screen draws
     * it so until the chat's page connects its own, rather than showing no
     * composer while the page arrives.
     */
    data class Composer(val state: ComposerState, val focused: Boolean)

    var composer: Composer? = null
        private set

    /** [chat] is about to replace the screen it was sent from, whose composer showed [composer]. */
    fun expect(chat: String, composer: Composer? = null, now: Long = SystemClock.uptimeMillis()) {
        expected = chat
        expectedAt = now
        this.composer = composer
    }

    /**
     * Whether the screen just made for [location] is the chat handed over,
     * taken once. A visit the navigator dropped after all (another
     * organization's chat, which starts the app over) leaves nothing behind
     * past a few seconds.
     */
    fun take(location: String, now: Long = SystemClock.uptimeMillis()): Boolean {
        val handed = expected == location && now - expectedAt in 0..EXPECTED_FOR_MS
        if (handed || now - expectedAt > EXPECTED_FOR_MS) expected = null
        if (!handed) composer = null
        return handed
    }

    /** The composer handed over with the chat, taken once. */
    fun takeComposer(): Composer? = composer.also { composer = null }

    /** Whether [location] is the chat on its way, without taking it (the visit's navigation options). */
    fun isExpected(location: String, now: Long = SystemClock.uptimeMillis()): Boolean =
        expected == location && now - expectedAt in 0..EXPECTED_FOR_MS

    /**
     * Runs [route] with [location]'s path properties saying [presentation], then
     * puts them back. A chat's rule says `replace_root`, and Hotwire Native
     * takes the presentation only from the path configuration, while the turn
     * replaces the current screen, which over a pushed page isn't the root.
     * Hotwire Native caches each location's properties and decides the
     * presentation synchronously as it routes, so the override lasts exactly
     * that one decision. The server's configuration arriving meanwhile would
     * clear that cache: it waits on the configuration's lock, which this holds
     * (Hotwire Native takes the same lock to apply a configuration and to
     * read properties, and the routing reads on this thread).
     */
    fun withPresentation(location: String, presentation: Presentation, route: () -> Unit) {
        val configuration = Hotwire.config.pathConfiguration
        synchronized(configuration) {
            val properties = configuration.properties(location)
            val before = properties[PRESENTATION]
            properties[PRESENTATION] = presentation.name.lowercase()
            try {
                route()
            } finally {
                if (before == null) properties.remove(PRESENTATION) else properties[PRESENTATION] = before
            }
        }
    }

    private const val PRESENTATION = "presentation"

    /** Longer than a screen takes to be made after the visit is proposed, on the slowest phone. */
    private const val EXPECTED_FOR_MS = 5_000L
}
