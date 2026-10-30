package com.chatwithwork.app.push

import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LivePresentationTest {
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
}
