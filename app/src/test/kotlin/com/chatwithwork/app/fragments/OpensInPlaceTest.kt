package com.chatwithwork.app.fragments

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import dev.hotwire.core.turbo.nav.Presentation
import dev.hotwire.core.turbo.visit.VisitAction
import org.junit.Test

class OpensInPlaceTest {
    private val newChat = "${AppConfig.baseUrl}/482139075/chats/new"
    private val newBot = "${AppConfig.baseUrl}/482139075/bots/new"

    @Test
    fun `New chat and New bot pushed from a page open in place`() {
        assertThat(OpensInPlace.applies(newChat, false, Presentation.DEFAULT, VisitAction.ADVANCE)).isTrue()
        assertThat(OpensInPlace.applies(newBot, false, Presentation.PUSH, VisitAction.ADVANCE)).isTrue()
    }

    @Test
    fun `other pages, modals, replacements and the place itself move as before`() {
        val chat = "${AppConfig.baseUrl}/482139075/chats/4"
        assertThat(OpensInPlace.applies(chat, false, Presentation.DEFAULT, VisitAction.ADVANCE)).isFalse()
        assertThat(OpensInPlace.applies(newChat, true, Presentation.DEFAULT, VisitAction.ADVANCE)).isFalse()
        assertThat(OpensInPlace.applies(newChat, false, Presentation.REPLACE_ROOT, VisitAction.ADVANCE)).isFalse()
        assertThat(OpensInPlace.applies(newChat, false, Presentation.DEFAULT, VisitAction.REPLACE)).isFalse()
    }

    @Test
    fun `the screen it opens takes it once, and only soon after`() {
        OpensInPlace.expect(newChat, now = 1_000)
        assertThat(OpensInPlace.take(newBot, now = 1_100)).isFalse()
        assertThat(OpensInPlace.take(newChat, now = 1_200)).isTrue()
        assertThat(OpensInPlace.take(newChat, now = 1_300)).isFalse()

        OpensInPlace.expect(newChat, now = 1_000)
        assertThat(OpensInPlace.take(newChat, now = 9_000)).isFalse()
    }
}
