package com.chatwithwork.app.fragments

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.presentation
import dev.hotwire.core.turbo.nav.Presentation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New chat turning into its chat in place: which visit is the turn, and the
 * chat screen that takes over without a skeleton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewChatHandOffTest {
    private val base = AppConfig.baseUrl
    private val chat = "$base/482139075/chats/42"

    @Test
    fun `the redirect from a question on New chat, New bot, the home or Decisions is the turn`() {
        listOf(
            "/482139075/chats/new",
            "/482139075/chats/new?project_id=3",
            "/482139075/bots/new",
            "/home",
            "/482139075/home",
            "/482139075/decisions"
        )
            .forEach { from ->
                assertWithMessage(
                    from
                ).that(NewChatHandOff.turnsInPlace("$base$from", chat, fromResponse = true)).isTrue()
            }
    }

    @Test
    fun `a link, another page, a chat about something, or another site is not`() {
        // A link (no form's response): the chat's own rule, a place.
        assertThat(NewChatHandOff.turnsInPlace("$base/482139075/decisions", chat, fromResponse = false)).isFalse()
        // From a chat, Bots, or a bot's page.
        listOf("/482139075/chats/7", "/482139075/bots", "/482139075/bots/7").forEach { from ->
            assertWithMessage(from).that(NewChatHandOff.turnsInPlace("$base$from", chat, fromResponse = true)).isFalse()
        }
        // To a chat opened about something on the page, or to anything but a chat.
        assertThat(
            NewChatHandOff.turnsInPlace("$base/482139075/decisions", "$chat?from=decisions", fromResponse = true)
        ).isFalse()
        assertThat(
            NewChatHandOff.turnsInPlace("$base/482139075/chats/new", "$base/482139075/bots/7", fromResponse = true)
        ).isFalse()
        assertThat(
            NewChatHandOff.turnsInPlace("$base/482139075/chats/new", "https://example.com/482139075/chats/42", true)
        ).isFalse()
    }

    @Test
    fun `the chat handed over is known once, and only for a few seconds`() {
        NewChatHandOff.expect(chat, now = 1_000)
        assertThat(NewChatHandOff.isExpected(chat, now = 1_100)).isTrue()
        assertThat(NewChatHandOff.take("$base/482139075/chats/43", now = 1_100)).isFalse()
        assertThat(NewChatHandOff.take(chat, now = 1_200)).isTrue()
        assertThat(NewChatHandOff.take(chat, now = 1_300)).isFalse()

        NewChatHandOff.expect(chat, now = 10_000)
        assertThat(NewChatHandOff.isExpected(chat, now = 16_000)).isFalse()
        assertThat(NewChatHandOff.take(chat, now = 16_000)).isFalse()
    }

    @Test
    fun `the chat's presentation is replaced only while the hand-off routes`() {
        val configuration = Hotwire.config.pathConfiguration
        val before = configuration.properties(chat).presentation
        var during: Presentation? = null

        NewChatHandOff.withPresentation(chat, Presentation.REPLACE) {
            during = configuration.properties(chat).presentation
        }

        assertThat(during).isEqualTo(Presentation.REPLACE)
        assertThat(configuration.properties(chat).presentation).isEqualTo(before)
    }
}
