package com.chatwithwork.app.push

import com.chatwithwork.app.push.LivePresentation.State
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What TalkBack is given for a Live Update (its ticker and the drawn card's
 * descriptions), in every state: the chat's title where it's known, which is
 * only when the person shows words in notifications; else the organization,
 * as the card heads itself.
 */
class LiveSpokenTest {
    private val title = "Acme renewal"
    private val organization = "Northwind Studio"
    private val words: (Int) -> String = { "Step $it" }

    private val states = mapOf(
        State.STARTING to LivePresentation("answering", "answer"),
        State.WORKING to LivePresentation("answering", "search", steps = 2, trail = "ss"),
        State.AGENTS to LivePresentation("answering", "run", steps = 2, trail = "ss", agents = 3),
        State.WRITING to LivePresentation("answering", "write", steps = 2, trail = "ss"),
        State.DECISION to LivePresentation("approval_waiting", steps = 2, trail = "ss"),
        State.INPUT to LivePresentation("input_requested", steps = 2, trail = "ss"),
        State.FINISHED to LivePresentation("answered", steps = 2, trail = "ss"),
        State.FAILED to LivePresentation("failed", steps = 2, trail = "ss"),
        State.STOPPED to LivePresentation("stopped", steps = 2, trail = "ss"),
        State.QUIET to LivePresentation("something_newer", steps = 2, trail = "ss")
    )

    @Test
    fun `every state is covered`() {
        assertThat(states.keys).containsExactlyElementsIn(State.entries)
        states.forEach { (state, shown) -> assertThat(shown.state).isEqualTo(state) }
    }

    @Test
    fun `TalkBack hears the chat's title first in every state where it's known`() {
        states.forEach { (state, plain) ->
            val shown = plain.copy(chatTitle = title)
            val step = shown.title(null, words)
            val spoken = shown.spoken(step, "2 steps", organization)
            assertThat(spoken).startsWith(title)
            assertThat(spoken).contains(step)
            assertThat(shown.collapsedLine(step)).startsWith(title)
            assertThat(state).isNotNull()
        }
    }

    @Test
    fun `a bot's run is heard by the bot's name and the title`() {
        val shown = LivePresentation("answering", "search", steps = 1, chatTitle = "Morning plan · Acme renewal")
        assertThat(shown.spoken("Searching Todoist", null, organization)).isEqualTo(
            "Morning plan · Acme renewal. Searching Todoist"
        )
    }

    @Test
    fun `without a title, or with words kept out of notifications, TalkBack hears the organization, never a title`() {
        // LiveTitles keeps no headline for someone who turned "Show the chat and its answer" off: chatTitle is null.
        states.forEach { (_, shown) ->
            assertThat(shown.chatTitle).isNull()
            val step = shown.title(null, words)
            val spoken = shown.spoken(step, null, organization)
            assertThat(spoken).isEqualTo("$organization. $step")
            assertThat(spoken).doesNotContain(title)
            assertThat(shown.collapsedLine(step)).isEqualTo(step)
        }
    }
}
