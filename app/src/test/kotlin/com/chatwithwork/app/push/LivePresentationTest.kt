package com.chatwithwork.app.push

import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LivePresentationTest {
    @Test
    fun `the metric dashboard shows only the actual previous kind before the current one`() {
        val state = LivePresentation("answering", "read")
        assertThat(state.previousStep(listOf("run", "search", "read"))).isEqualTo("search")
        assertThat(state.previousStep(listOf("read"))).isNull()
        assertThat(state.previousStep(emptyList())).isNull()
        assertThat(state.previousStep(listOf("search", "write"))).isNull()
    }

    @Test
    fun `the current action survives a missing or blank activity label`() {
        val running = LivePresentation("answering", "run")
        val words: (Int) -> String = { id ->
            when (id) {
                R.string.live_update_running -> "Running a task"
                else -> error("Unexpected fallback")
            }
        }
        assertThat(running.title("Running an agent", words)).isEqualTo("Running an agent")
        assertThat(running.title(null, words)).isEqualTo("Running a task")
        assertThat(running.title(" \n ", words)).isEqualTo("Running a task")
    }

    @Test
    fun `thinking is distinct from streaming the answer`() {
        val thinking = LivePresentation("answering", "answer")
        val writing = LivePresentation("answering", "write")
        assertThat(thinking.headline).isEqualTo(R.string.live_update_answering)
        assertThat(thinking.symbol).isEqualTo("more_horiz")
        assertThat(LiveUpdates.chipWord("answer")).isEqualTo(R.string.live_update_chip_think)
        assertThat(writing.headline).isEqualTo(R.string.live_update_writing)
        assertThat(writing.symbol).isEqualTo("edit_note")
    }

    @Test
    fun `ended and unknown phases do not present a stale working label`() {
        val ready = LivePresentation("answered", "run")
        assertThat(ready.title("Running an agent") { "Answer ready" }).isEqualTo("Answer ready")
        val unknown = LivePresentation("future_state", "run")
        assertThat(unknown.color).isEqualTo(R.color.lw_ink_muted)
        assertThat(unknown.symbol).isEqualTo("sync_problem")
        assertThat(unknown.headline).isEqualTo(R.string.live_update_unknown)
    }

    @Test
    fun `only running answers retain the elapsed timer`() {
        assertThat(LivePresentation("answering").isWorking).isTrue()
        for (phase in listOf("answered", "approval_waiting", "input_requested", "failed", "stopped", "future_state")) {
            assertThat(LivePresentation(phase).isWorking).isFalse()
        }
    }

    @Test
    fun `decisions open the chat to review and never approve from a notification`() {
        assertThat(LivePresentation("approval_waiting").action).isEqualTo(R.string.live_update_review)
        assertThat(LivePresentation("input_requested").action).isEqualTo(R.string.live_update_reply)
        assertThat(LivePresentation("answered").action).isEqualTo(R.string.live_update_read)
        assertThat(LivePresentation("future_state").headline).isEqualTo(R.string.live_update_unknown)
    }

    @Test
    fun `a progress message's run fields are optional`() {
        val base = mapOf("kind" to "progress", "path" to "/482139075/chats/42", "thread" to "t", "phase" to "answering")

        val named = LiveProgress.from(base + mapOf("run" to "a1", "revision" to "7", "started_at" to "1000"))
        assertThat(named?.runRef).isEqualTo(LiveRunRef("a1", 1_000_000, 7))

        val waiting = LiveProgress.from(
            base + mapOf(
                "phase" to "approval_waiting",
                "waiting_since" to "2000",
                "services" to "[\"slack\",\"mcp\"]",
                "service" to "slack",
                "trail" to "srA",
                "agents" to "3",
                "heard_at" to "3000"
            )
        )
        assertThat(waiting?.isWaiting).isTrue()
        assertThat(waiting?.isEnd).isFalse()
        assertThat(waiting?.waitingSince).isEqualTo(2_000_000)
        assertThat(waiting?.services).containsExactly("slack", "mcp").inOrder()
        assertThat(waiting?.trail).isEqualTo("srA")
        assertThat(waiting?.agents).isEqualTo(3)
        assertThat(waiting?.heardAt).isEqualTo(3_000_000)
        assertThat(LiveProgress.from(base + ("phase" to "answered"))?.isEnd).isTrue()
        assertThat(LiveProgress.from(base)?.isEnd).isFalse()

        val older = LiveProgress.from(base)
        assertThat(older?.run).isNull()
        assertThat(older?.runRef).isNull()
        assertThat(older?.revision).isEqualTo(0)
        assertThat(older?.startedAt).isNull()
        assertThat(LiveProgress.from(base + ("run" to " "))?.run).isNull()
    }
}
