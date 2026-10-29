package com.chatwithwork.app.push

import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LivePresentationTest {
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
