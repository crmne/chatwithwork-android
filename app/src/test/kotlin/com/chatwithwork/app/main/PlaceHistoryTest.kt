package com.chatwithwork.app.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Back at a place, as a browser's back: the places the person was at, and what back does (GoingBack). */
class PlaceHistoryTest {
    private val base = "https://chatwithwork.com/482139075"
    private val home = "$base/decisions"
    private val chatA = "$base/chats/1"
    private val chatB = "$base/chats/2"
    private val chatC = "$base/chats/3"
    private val newChat = "$base/chats/new"

    private fun PlaceHistory.visit(location: String) = arrived(location, atHome = location == home)

    /** Presses back as MainActivity does: the place before, else the home unless there, else null (leave). */
    private fun PlaceHistory.back(worth: (String) -> Boolean = { true }): String? {
        val here = current
        val step = GoingBack.step(false, 1, next(worth), atHome = here == home, home = home)
        val to = (step as? GoingBack.Step.GoTo)?.location ?: return null
        returnTo(to)
        visit(to)
        return to
    }

    @Test
    fun `back goes to the place before, then home, then leaves`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)

        assertThat(places.back()).isEqualTo(chatA)
        assertThat(places.back()).isEqualTo(home)
        assertThat(places.back()).isNull()
    }

    @Test
    fun `going back adds nothing, so there's no ping-pong`() {
        val places = PlaceHistory()
        places.visit(home)
        places.visit(chatA)
        places.visit(chatB)

        assertThat(places.back()).isEqualTo(chatA)
        assertThat(places.entries).containsExactly(home)
        assertThat(places.back()).isEqualTo(home)
        assertThat(places.back()).isNull()
    }

    @Test
    fun `a place already in the history moves to the top, and the place on screen adds nothing`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)
        places.visit(chatA)
        assertThat(places.entries).containsExactly(chatB).inOrder()

        places.visit(chatC)
        places.visit(chatC)
        assertThat(places.entries).containsExactly(chatA, chatB).inOrder()
    }

    @Test
    fun `the history keeps the newest few`() {
        val places = PlaceHistory()
        (1..9).forEach { places.visit("$base/chats/$it") }

        assertThat(places.entries).hasSize(PlaceHistory.CAP)
        assertThat(places.entries.first()).isEqualTo("$base/chats/8")
    }

    @Test
    fun `reaching home by back clears what's below it, so back at home leaves`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(home)
        places.visit(chatB)

        assertThat(places.back()).isEqualTo(home)
        assertThat(places.entries).isEmpty()
        assertThat(places.back()).isNull()
    }

    @Test
    fun `home reached by a tap still goes back to where the person was`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(home)

        assertThat(places.back()).isEqualTo(chatA)
    }

    @Test
    fun `an empty New chat is skipped, and the chat it started stands in its place`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(newChat)
        places.visit(chatB)
        val worth = { location: String -> location != newChat }

        assertThat(places.back(worth)).isEqualTo(chatA)
        assertThat(places.entries).isEmpty()
    }

    @Test
    fun `a place that's gone is skipped, on to the next, or home`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)
        places.visit(chatC)

        val to = places.next()!!
        places.returnTo(to)
        assertThat(places.failed(to)).isTrue()
        assertThat(places.next()).isEqualTo(chatA)

        places.returnTo(chatA)
        assertThat(places.failed(chatA)).isTrue()
        assertThat(places.next()).isNull()
        assertThat(GoingBack.step(false, 1, places.next(), atHome = false, home = home))
            .isEqualTo(GoingBack.Step.GoTo(home))
    }

    @Test
    fun `a place that fails without back opening it changes nothing but leaves the history`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)

        assertThat(places.failed(chatA)).isFalse()
        assertThat(places.entries).isEmpty()
    }

    @Test
    fun `a chat deleted on screen is never put on the list, so back from Decisions skips it`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)
        // Its own Delete: the chat gives way to Decisions, and the page says what it deleted after.
        places.visit(home)
        places.forget(chatB)
        assertThat(places.entries).containsExactly(chatA)

        // Said before the visit lands, it never goes on at all.
        places.visit(chatC)
        places.forget(chatC)
        places.visit(home)
        assertThat(places.entries).containsExactly(chatA)

        assertThat(places.back()).isEqualTo(chatA)
        assertThat(places.back()).isEqualTo(home)
    }

    @Test
    fun `a chat deleted from the sidebar leaves the list wherever it is`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)
        places.visit(chatC)
        places.forget(chatA)

        assertThat(places.entries).containsExactly(chatB)
        assertThat(places.back()).isEqualTo(chatB)
        assertThat(places.back()).isEqualTo(home)
    }

    @Test
    fun `a cold start on a chat has nothing before it, so back goes home, then leaves`() {
        val places = PlaceHistory()
        places.visit(chatA)

        assertThat(places.back()).isEqualTo(home)
        assertThat(places.back()).isNull()
    }

    @Test
    fun `what Decisions changes in its own address is the same place`() {
        assertThat(PlaceHistory.same("$home?view=now&decision=t7", home)).isTrue()
        assertThat(PlaceHistory.same("$home?bot=4", home)).isFalse()
        assertThat(PlaceHistory.same("$base/settings?tab=assistant", "$base/settings")).isFalse()

        val places = PlaceHistory()
        places.visit(home)
        places.visit("$home?decision=t7")
        assertThat(places.entries).isEmpty()
    }

    @Test
    fun `the history outlives the process, but never doubles up`() {
        val places = PlaceHistory()
        places.visit(chatA)
        places.visit(chatB)

        val restored = PlaceHistory()
        restored.restore(places.saved())
        assertThat(restored.entries).containsExactly(chatA)
        restored.restore(listOf(chatC))
        assertThat(restored.entries).containsExactly(chatA)
    }

    @Test
    fun `back closes the sidebar first, pops a drill-down, else goes to the place before, home, or leaves`() {
        assertThat(GoingBack.step(true, 1, chatA, atHome = false, home = home)).isEqualTo(GoingBack.Step.CloseSidebar)
        assertThat(GoingBack.step(true, 3, chatA, atHome = false, home = home)).isEqualTo(GoingBack.Step.CloseSidebar)
        assertThat(GoingBack.step(false, 2, chatA, atHome = false, home = home)).isEqualTo(GoingBack.Step.Pop)
        assertThat(GoingBack.step(false, 1, chatA, atHome = true, home = home)).isEqualTo(GoingBack.Step.GoTo(chatA))
        assertThat(GoingBack.step(false, 1, null, atHome = false, home = home)).isEqualTo(GoingBack.Step.GoTo(home))
        assertThat(GoingBack.step(false, 1, null, atHome = true, home = home)).isEqualTo(GoingBack.Step.Leave)
        assertThat(GoingBack.step(false, 1, null, atHome = false, home = null)).isEqualTo(GoingBack.Step.Leave)
    }
}
