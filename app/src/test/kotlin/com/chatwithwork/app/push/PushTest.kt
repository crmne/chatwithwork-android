package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PushTest {
    private val path = "/482139075/chats/42"

    @Test
    fun `reads an approval push`() {
        val push = Push.from(
            mapOf(
                "kind" to "approval_waiting",
                "path" to path,
                "thread" to "chat-482139075-42",
                "title" to "Acme",
                "body" to "A change is waiting for your approval in Slack."
            )
        )

        assertThat(push).isNotNull()
        assertThat(push!!.kind).isEqualTo(Push.Kind.APPROVAL_WAITING)
        assertThat(push.url).isEqualTo("${AppConfig.baseUrl}$path")
        assertThat(push.thread).isEqualTo("chat-482139075-42")
        assertThat(push.title).isEqualTo("Acme")
    }

    @Test
    fun `the thread defaults to the page, so a newer push for a chat replaces the older`() {
        val push = Push.from(mapOf("kind" to "input_requested", "path" to path))
        assertThat(push?.thread).isEqualTo(path)
        assertThat(push?.title).isNull()
    }

    @Test
    fun `knows when a change was decided elsewhere`() {
        assertThat(Push.from(mapOf("kind" to "resolved", "path" to path))?.kind).isEqualTo(Push.Kind.RESOLVED)
    }

    @Test
    fun `drops pushes it can't trust or doesn't know`() {
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "https://evil.example/chats/42"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "//evil.example/chats/42"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting"))).isNull()
        assertThat(Push.from(mapOf("kind" to "marketing", "path" to path))).isNull()
    }
}
