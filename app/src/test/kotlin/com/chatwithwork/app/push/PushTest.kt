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
    fun `reads the server's other kinds, which don't ring like approvals`() {
        val answered = Push.from(mapOf("kind" to "answered", "path" to path))
        assertThat(answered?.kind).isEqualTo(Push.Kind.ANSWERED)
        assertThat(answered?.waitsForPerson).isFalse()
        assertThat(Push.from(mapOf("kind" to "handed_off", "path" to path))?.kind).isEqualTo(Push.Kind.HANDED_OFF)
        assertThat(Push.from(mapOf("kind" to "failed", "path" to path))?.kind).isEqualTo(Push.Kind.FAILED)
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to path))?.waitsForPerson).isTrue()
        assertThat(Push.from(mapOf("kind" to "input_requested", "path" to path))?.waitsForPerson).isTrue()
    }

    @Test
    fun `knows when a change was decided elsewhere`() {
        assertThat(Push.from(mapOf("kind" to "resolved", "path" to path))?.kind).isEqualTo(Push.Kind.RESOLVED)
    }

    @Test
    fun `carries the id its words are fetched with, never the words`() {
        val push = Push.from(mapOf("kind" to "answered", "path" to path, "notification_id" to "c2VjcmV0--aXY--dGFn"))
        assertThat(push?.notificationId).isEqualTo("c2VjcmV0--aXY--dGFn")
        assertThat(Push.from(mapOf("kind" to "answered", "path" to path))?.notificationId).isNull()
    }

    @Test
    fun `reads a Live Update's step and its end, and leaves them out of notifications`() {
        val data =
            mapOf(
                "kind" to "progress",
                "path" to path,
                "thread" to "chat-482139075-42",
                "phase" to "answering",
                "message" to "Searching Todoist",
                "step" to "search",
                "steps" to "3",
                "sent_at" to "1793040000123"
            )
        val step = LiveProgress.from(data)
        assertThat(step).isNotNull()
        assertThat(step!!.isWorking).isTrue()
        assertThat(step.message).isEqualTo("Searching Todoist")
        assertThat(step.steps).isEqualTo(3)
        assertThat(step.sentAt).isEqualTo(1793040000123)
        assertThat(Push.from(data)).isNull()

        val end = LiveProgress.from(
            data + mapOf("phase" to "answered", "message" to "Your answer is ready.") - "step" - "steps"
        )
        assertThat(end?.isWorking).isFalse()
        assertThat(end?.steps).isEqualTo(0)

        assertThat(LiveProgress.from(data + ("path" to "https://evil.example/chats/42"))).isNull()
        assertThat(LiveProgress.from(data - "thread")).isNull()
        assertThat(LiveProgress.from(data + ("kind" to "answered"))).isNull()
    }

    @Test
    fun `asks this server for a notification's words, only by an id shaped like the server's`() {
        assertThat(NotificationWords.url("c2VjcmV0--aXY--dGFn_x-y"))
            .isEqualTo("${AppConfig.baseUrl}/native/notifications/c2VjcmV0--aXY--dGFn_x-y")
        assertThat(NotificationWords.url("../../settings")).isNull()
        assertThat(NotificationWords.url("abc?x=1")).isNull()
        assertThat(NotificationWords.url("")).isNull()
    }

    @Test
    fun `reads the server's words, which need a title and a body`() {
        assertThat(NotificationWords.parse("""{"title":"Q3 numbers","body":"Revenue grew 12%."}"""))
            .isEqualTo(NotificationWords.Text("Q3 numbers", null, "Revenue grew 12%."))
        val wait = """{"title":"Acme launch","subtitle":"Waiting for your approval in Slack","body":"Post the recap"}"""
        assertThat(NotificationWords.parse(wait)?.subtitle).isEqualTo("Waiting for your approval in Slack")
        assertThat(NotificationWords.parse("""{"title":"","body":"x"}""")).isNull()
        assertThat(NotificationWords.parse("not json")).isNull()
    }

    @Test
    fun `drops pushes it can't trust or doesn't know`() {
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "https://evil.example/chats/42"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "//evil.example/chats/42"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting"))).isNull()
        assertThat(Push.from(mapOf("kind" to "marketing", "path" to path))).isNull()
    }
}
