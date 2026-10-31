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
        assertThat(LiveUpdates.chipWord("answer")).isEqualTo(R.string.live_update_chip_think)
        assertThat(writing.headline).isEqualTo(R.string.live_update_writing)
    }

    @Test
    fun `ended and unknown phases do not present a stale working label`() {
        val ready = LivePresentation("answered", "run")
        assertThat(ready.title("Running an agent") { "Answer ready" }).isEqualTo("Answer ready")
        val unknown = LivePresentation("future_state", "run")
        assertThat(unknown.color).isEqualTo(R.color.lw_ink_muted)
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
        assertThat(LivePresentation("answered").action).isNull()
        assertThat(LivePresentation("failed").action).isEqualTo(R.string.live_update_open)
        assertThat(LivePresentation("future_state").action).isEqualTo(R.string.live_update_open)
        assertThat(LivePresentation("future_state").headline).isEqualTo(R.string.live_update_unknown)
    }

    @Test
    fun `every state comes only from what the server said`() {
        val working = LivePresentation("answering", "search", steps = 5)
        assertThat(LivePresentation("answering", "answer").state).isEqualTo(LivePresentation.State.STARTING)
        assertThat(LivePresentation("answering").state).isEqualTo(LivePresentation.State.WORKING)
        assertThat(LivePresentation("answering", "answer", steps = 1).state).isEqualTo(LivePresentation.State.WORKING)
        assertThat(working.state).isEqualTo(LivePresentation.State.WORKING)
        assertThat(working.copy(agents = 1).state).isEqualTo(LivePresentation.State.WORKING)
        assertThat(working.copy(agents = 2).state).isEqualTo(LivePresentation.State.AGENTS)
        assertThat(LivePresentation("approval_waiting").state).isEqualTo(LivePresentation.State.DECISION)
        assertThat(LivePresentation("input_requested").state).isEqualTo(LivePresentation.State.INPUT)
        assertThat(LivePresentation("answered").state).isEqualTo(LivePresentation.State.FINISHED)
        assertThat(LivePresentation("failed").state).isEqualTo(LivePresentation.State.FAILED)
        assertThat(LivePresentation("stopped").state).isEqualTo(LivePresentation.State.STOPPED)
        assertThat(LivePresentation("future_state").state).isEqualTo(LivePresentation.State.STOPPED)
    }

    @Test
    fun `the bot's face follows the step, and failing closes its eyes`() {
        val faces = mapOf(
            "answer" to LivePresentation.Face.UP,
            "search" to LivePresentation.Face.RIGHT,
            "list" to LivePresentation.Face.LEFT,
            "read" to LivePresentation.Face.DOWN,
            "use" to LivePresentation.Face.FOCUS,
            "plan" to LivePresentation.Face.UP,
            "run" to LivePresentation.Face.FOCUS,
            "write" to LivePresentation.Face.DOWN,
            "ask" to LivePresentation.Face.BROW
        )
        faces.forEach { (step, face) ->
            assertThat(LivePresentation("answering", step, steps = 1).face).isEqualTo(face)
        }
        assertThat(LivePresentation("answering", "answer").face).isEqualTo(LivePresentation.Face.OPEN)
        assertThat(LivePresentation("approval_waiting").face).isEqualTo(LivePresentation.Face.WIDE)
        assertThat(LivePresentation("input_requested").face).isEqualTo(LivePresentation.Face.BROW)
        assertThat(LivePresentation("answered").face).isEqualTo(LivePresentation.Face.HAPPY)
        assertThat(LivePresentation("failed").face).isEqualTo(LivePresentation.Face.CLOSED)
        assertThat(LivePresentation("stopped").face).isEqualTo(LivePresentation.Face.FLAT)
    }

    @Test
    fun `the chip says a service whose name fits, else the step's word`() {
        val word: (Int) -> String = { id ->
            when (id) {
                R.string.live_update_chip_search -> "Search"
                R.string.live_update_chip_read -> "Read"
                R.string.live_update_chip_start -> "Start"
                R.string.live_update_chip_agents -> "%1\$d run"
                R.string.live_update_review -> "Review"
                R.string.live_update_reply -> "Reply"
                R.string.live_update_chip_ready -> "Ready"
                else -> error("Unexpected word")
            }
        }
        assertThat(LivePresentation("answering", "search", 1, service = "google_drive").chip(word)).isEqualTo("Drive")
        assertThat(LivePresentation("answering", "read", 1, service = "hubspot").chip(word)).isEqualTo("Read")
        assertThat(
            LivePresentation("answering", "search", 1, service = "someones_server").chip(word)
        ).isEqualTo("Search")
        assertThat(LivePresentation("answering", "read", 1, service = "mcp").chip(word)).isEqualTo("Read")
        assertThat(LivePresentation("answering", "answer").chip(word)).isEqualTo("Start")
        assertThat(LivePresentation("answering", "use", 9, agents = 3).chip(word)).isEqualTo("3 run")
        assertThat(LivePresentation("approval_waiting", service = "slack").chip(word)).isEqualTo("Review")
        assertThat(LivePresentation("input_requested").chip(word)).isEqualTo("Reply")
        assertThat(LivePresentation("answered").chip(word)).isEqualTo("Ready")
    }

    @Test
    fun `a decision names its service only when it is a known name`() {
        val words: (Int) -> String = { id ->
            when (id) {
                R.string.live_update_approval_in -> "Approve a change in %1\$s"
                R.string.live_update_approval -> "Approve a change"
                R.string.live_update_starting -> "Starting"
                else -> error("Unexpected word")
            }
        }
        val waiting = LivePresentation("approval_waiting")
        assertThat(
            waiting.copy(service = "slack").title("A change is waiting.", words)
        ).isEqualTo("Approve a change in Slack")
        assertThat(waiting.copy(service = "mcp").title(null, words)).isEqualTo("Approve a change")
        assertThat(waiting.copy(service = "someones_server").title(null, words)).isEqualTo("Approve a change")
        assertThat(LivePresentation("answering", "answer").title("Answering", words)).isEqualTo("Starting")
    }

    @Test
    fun `a progress message's trail lets only its letters through`() {
        assertThat(LiveProgress.trail("srAw")).isEqualTo("srAw")
        assertThat(LiveProgress.trail("s<b>r")).isEqualTo("sr")
        assertThat(LiveProgress.trail("z9")).isNull()
        assertThat(LiveProgress.trail(null)).isNull()
        assertThat(LiveProgress.trail("s".repeat(60))).hasLength(48)
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
