package com.chatwithwork.app.composer

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

/** New chat's draft: kept per organization when the modal closes, back in the next New chat, gone once sent. */
class NewChatDraftsTest {
    private val base = AppConfig.baseUrl
    private val newChat = "$base/482139075/chats/new"
    private val otherOrganization = "$base/731904562/chats/new"

    @After
    fun forget() {
        listOf(newChat, otherOrganization).forEach(NewChatDrafts::clear)
    }

    @Test
    fun `a closed New chat's words come back in the next one, once`() {
        NewChatDrafts.keep(newChat, "Draft about the Q4 plan")

        assertThat(NewChatDrafts.take("$newChat?project_id=3")).isEqualTo("Draft about the Q4 plan")
        assertThat(NewChatDrafts.take(newChat)).isNull()
    }

    @Test
    fun `each organization has its own draft`() {
        NewChatDrafts.keep(newChat, "For Northwind")

        assertThat(NewChatDrafts.take(otherOrganization)).isNull()
        assertThat(NewChatDrafts.take(newChat)).isEqualTo("For Northwind")
    }

    @Test
    fun `an empty field or a sent chat leaves no draft`() {
        NewChatDrafts.keep(newChat, "Something")
        NewChatDrafts.keep(newChat, "  ")
        assertThat(NewChatDrafts.take(newChat)).isNull()

        NewChatDrafts.keep(newChat, "Something")
        NewChatDrafts.clear(newChat)
        assertThat(NewChatDrafts.take(newChat)).isNull()
    }

    @Test
    fun `signing out forgets every organization's draft`() {
        NewChatDrafts.keep(newChat, "For Northwind")
        NewChatDrafts.keep(otherOrganization, "For the other one")

        NewChatDrafts.clearAll()

        assertThat(NewChatDrafts.take(newChat)).isNull()
        assertThat(NewChatDrafts.take(otherOrganization)).isNull()
    }

    @Test
    fun `only New chat keeps a draft, never a chat's own composer`() {
        NewChatDrafts.keep("$base/482139075/chats/42", "A reply")

        assertThat(NewChatDrafts.take(newChat)).isNull()
        assertThat(NewChatDrafts.take("$base/482139075/chats/42")).isNull()
    }

    @Test
    fun `a share's words join the draft rather than replacing it`() {
        assertThat(NewChatDrafts.joined("Draft", "")).isEqualTo("Draft")
        assertThat(NewChatDrafts.joined("Draft", "Shared link")).isEqualTo("Draft\n\nShared link")
        assertThat(NewChatDrafts.joined(null, "Shared link")).isNull()
        assertThat(NewChatDrafts.joined("Draft", "Draft")).isNull()
        assertThat(NewChatDrafts.joined("Draft and more", "Draft")).isEqualTo("Draft and more")
    }
}
