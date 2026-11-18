package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTitlesTest {
    @Test
    fun `the chat's title leads, a bot's name before it, and a chat with none yet is a new chat`() {
        assertThat(LiveTitles.Title("Acme renewal", titled = true).headline("New chat")).isEqualTo("Acme renewal")
        assertThat(LiveTitles.Title("Pipeline review", "Morning plan", true).headline("New chat"))
            .isEqualTo("Morning plan · Pipeline review")
        assertThat(
            LiveTitles.Title(bot = "Morning plan", titled = false).headline("New chat")
        ).isEqualTo("Morning plan")
        assertThat(LiveTitles.Title(titled = false).headline("New chat")).isEqualTo("New chat")
    }

    @Test
    fun `nothing where the person keeps words out of notifications, so the step's words lead`() {
        val empty = LiveTitles.parse("{}")!!
        assertThat(empty.isEmpty).isTrue()
        assertThat(empty.headline("New chat")).isNull()
        assertThat(LiveTitles.Title(" ", titled = true).headline("New chat")).isNull()
    }

    @Test
    fun `asks once a run, and again while the chat has no title`() {
        val titled = LiveTitles.Kept("run1", LiveTitles.Title("Acme", titled = true))
        assertThat(LiveTitles.needsFetch(null, "run1")).isTrue()
        assertThat(LiveTitles.needsFetch(titled, "run1")).isFalse()
        assertThat(LiveTitles.needsFetch(titled, "run2")).isTrue()
        assertThat(LiveTitles.needsFetch(LiveTitles.Kept("run1", LiveTitles.Title(titled = false)), "run1")).isTrue()
        assertThat(LiveTitles.needsFetch(LiveTitles.Kept("run1", LiveTitles.Title()), "run1")).isFalse()
    }

    @Test
    fun `asks only this build's server, for an id shaped like the server's`() {
        assertThat(
            LiveTitles.url("uEMJ-NNHE7_Vu--fBTj==")
        ).isEqualTo("${AppConfig.baseUrl}/native/live_titles/uEMJ-NNHE7_Vu--fBTj==")
        assertThat(LiveTitles.url("../notifications/1")).isNull()
        assertThat(LiveTitles.url("a/b")).isNull()
        assertThat(LiveTitles.parse("not json")).isNull()
    }

    @Test
    fun `a Live Update message carries the id the title is asked for with, never the title`() {
        val data = mapOf(
            "kind" to "progress",
            "path" to "/1000001/chats/42",
            "thread" to "chat-1000001-42",
            "phase" to "answering",
            "title_ref" to "uEMJNNHE7Vu--fBTj"
        )
        assertThat(LiveProgress.from(data)?.titleRef).isEqualTo("uEMJNNHE7Vu--fBTj")
        assertThat(LiveProgress.from(data - "title_ref")?.titleRef).isNull()
    }
}
