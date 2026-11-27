package com.chatwithwork.app.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** New chat's words in the page composer, per organization, as evaluateJavascript answers them. */
class WebNewChatDraftsTest {
    private fun answer(json: String) = "\"" + json.replace("\"", "\\\"") + "\""

    @Test
    fun `an organization's New chat keeps words only when its own key has them`() {
        val drafts = WebNewChatDrafts()
        drafts.read("482139075", answer("""{"key":"new_chat_7","drafted":["new_chat_9"]}"""))
        assertThat(drafts.has("482139075")).isFalse()

        // Another organization's page names its key: its words don't count for the first.
        drafts.read("100000001", answer("""{"key":"new_chat_9","drafted":["new_chat_9"]}"""))
        assertThat(drafts.has("100000001")).isTrue()
        assertThat(drafts.has("482139075")).isFalse()

        // A page without the composer still says what's drafted now.
        drafts.read("482139075", answer("""{"key":null,"drafted":["new_chat_7"]}"""))
        assertThat(drafts.has("482139075")).isTrue()
        assertThat(drafts.has("100000001")).isFalse()
    }

    @Test
    fun `the keys learned outlive the process, and the words are read again from the page`() {
        val drafts = WebNewChatDrafts()
        drafts.read("482139075", answer("""{"key":"new_chat_7","drafted":[]}"""))

        val restored = WebNewChatDrafts()
        restored.restore(drafts.saved())
        assertThat(restored.has("482139075")).isFalse()
        restored.read("482139075", answer("""{"key":null,"drafted":["new_chat_7"]}"""))
        assertThat(restored.has("482139075")).isTrue()
        restored.restore(listOf("broken", "=x", "y="))
        assertThat(restored.saved()).containsExactly("482139075=new_chat_7")
    }

    @Test
    fun `an organization whose key the app hasn't seen keeps none`() {
        val drafts = WebNewChatDrafts()
        drafts.read(null, answer("""{"key":"new_chat_7","drafted":["new_chat_7"]}"""))
        assertThat(drafts.has("482139075")).isFalse()
        assertThat(drafts.has(null)).isFalse()
    }

    @Test
    fun `anything but the script's answer changes nothing`() {
        assertThat(WebNewChatDrafts.parse(null)).isNull()
        assertThat(WebNewChatDrafts.parse("null")).isNull()
        assertThat(WebNewChatDrafts.parse("\"not json\"")).isNull()
        assertThat(WebNewChatDrafts.parse(answer("""{"key":3,"drafted":[1,"new_chat_2"]}""")))
            .isEqualTo(WebNewChatDrafts.Reading(null, setOf("new_chat_2")))
    }

    @Test
    fun `the script reads the composer's own draft key and localStorage`() {
        assertThat(WebNewChatDrafts.SCRIPT).contains("data-chat-draft-key-value^='new_chat_'")
        assertThat(WebNewChatDrafts.SCRIPT).contains("chat-with-work:draft:")
        assertThat(WebNewChatDrafts.SCRIPT).contains("JSON.stringify")
    }
}
