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
        assertThat(LivePresentation("future_state").state).isEqualTo(LivePresentation.State.QUIET)
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
        assertThat(LiveProgress.from(base + ("phase" to "something_newer"))?.isEnd).isFalse()

        val older = LiveProgress.from(base)
        assertThat(older?.run).isNull()
        assertThat(older?.runRef).isNull()
        assertThat(older?.revision).isEqualTo(0)
        assertThat(older?.startedAt).isNull()
        assertThat(LiveProgress.from(base + ("run" to " "))?.run).isNull()
    }

    @Test
    fun `each step is as tall as the time pace says it took`() {
        val shown = LivePresentation("answering", "read", trail = "srlu", pace = "1234")
        assertThat(shown.ticks.map { it.height }).containsExactly(6f, 13f, 21f, 29f).inOrder()
    }

    @Test
    fun `without a whole pace every step is the same height`() {
        for (pace in listOf(null, "12", "12345", "1x34", "0123")) {
            val shown = LivePresentation("answering", "read", trail = "srlu", pace = pace)
            assertThat(shown.ticks.map { it.height }.toSet()).containsExactly(14f)
        }
    }

    @Test
    fun `a step that failed or was denied is marked, and an older server's count stays even`() {
        val shown = LivePresentation("answering", "read", trail = "sRl")
        assertThat(shown.ticks.map { it.refused }).containsExactly(false, true, false).inOrder()
        val counted = LivePresentation("answering", "read", steps = 300).ticks
        assertThat(counted).hasSize(48)
        assertThat(counted.map { it.height }.toSet()).containsExactly(14f)
    }

    @Test
    fun `three bots then a count for agents`() {
        val two = LivePresentation("answering", "use", agents = 2)
        assertThat(two.agentBots).isEqualTo(2)
        assertThat(two.moreAgents).isEqualTo(0)
        val seven = LivePresentation("answering", "use", agents = 7)
        assertThat(seven.agentBots).isEqualTo(3)
        assertThat(seven.moreAgents).isEqualTo(4)
    }

    @Test
    fun `failed is closed eyes, never a joke`() {
        assertThat(LivePresentation("failed").face).isEqualTo(LivePresentation.Face.CLOSED)
    }

    @Test
    fun `a band shows only when the person is needed or it failed`() {
        for (phase in listOf("approval_waiting", "input_requested", "failed")) {
            assertThat(LivePresentation(phase).hasBand).isTrue()
        }
        for (phase in listOf("answering", "answered", "stopped", "future_state")) {
            assertThat(LivePresentation(phase).hasBand).isFalse()
        }
    }

    @Test
    fun `writing has its own road, and agents outrank it`() {
        assertThat(LivePresentation("answering", "write", 3).state).isEqualTo(LivePresentation.State.WRITING)
        assertThat(LivePresentation("answering", "write", 3, agents = 2).state)
            .isEqualTo(LivePresentation.State.AGENTS)
    }

    @Test
    fun `the bot holds up its tool only while it works`() {
        assertThat(LivePresentation("answering", "search", 1).tool).isEqualTo(LivePresentation.Tool.SEARCH)
        assertThat(LivePresentation("answering", "use", 1, agents = 3).tool).isNull()
        assertThat(LivePresentation("approval_waiting", "use", 1).tool).isNull()
    }

    @Test
    fun `stops are the services behind the bot, newest kept`() {
        val shown = LivePresentation(
            "answering",
            "read",
            service = "hubspot",
            services = listOf("slack", "unknown", "todoist", "gmail", "dropbox", "asana", "linear", "slack", "hubspot")
        )
        assertThat(shown.stops).containsExactly("todoist", "gmail", "dropbox", "asana", "linear").inOrder()
    }

    @Test
    fun `the headline splits around the service it names`() {
        val shown = LivePresentation("answering", "search", 1, service = "google_drive")
        assertThat(shown.split("Searching Drive")).isEqualTo("Searching" to "Drive")
        val files = LivePresentation("answering", "read", 1, service = "hubspot")
        assertThat(files.split("Reading 12 files in HubSpot")).isEqualTo("Reading 12 files" to "HubSpot")
        assertThat(shown.split("Thinking")).isNull()
        assertThat(LivePresentation("answering", "use", 1, service = "mcp").split("Using a tool")).isNull()
    }

    @Test
    fun `android's template shows the bot mark's faces, and failed is flat`() {
        assertThat(LivePresentation("answering", "answer").mark).isEqualTo(LivePresentation.Mark.THINKING)
        assertThat(LivePresentation("answering", "search", steps = 2).mark).isEqualTo(LivePresentation.Mark.THINKING)
        assertThat(LivePresentation("answering", "read", steps = 2).mark).isEqualTo(LivePresentation.Mark.WRITING)
        assertThat(LivePresentation("answering", "write", steps = 2).mark).isEqualTo(LivePresentation.Mark.WRITING)
        assertThat(LivePresentation("approval_waiting").mark).isEqualTo(LivePresentation.Mark.WAITING)
        assertThat(LivePresentation("input_requested").mark).isEqualTo(LivePresentation.Mark.WAITING)
        assertThat(LivePresentation("answered").mark).isEqualTo(LivePresentation.Mark.HAPPY)
        assertThat(LivePresentation("failed").mark).isEqualTo(LivePresentation.Mark.FLAT)
        assertThat(LivePresentation("stopped").mark).isEqualTo(LivePresentation.Mark.FLAT)
    }

    @Test
    fun `the status bar chip says what it's doing or what it needs, and nothing once it's over`() {
        assertThat(LivePresentation("answering", "answer").chip).isEqualTo(R.string.live_update_chip_think)
        assertThat(LivePresentation("answering", "search", steps = 1).chip).isEqualTo(R.string.live_update_chip_search)
        assertThat(LivePresentation("answering", "write", steps = 1).chip).isEqualTo(R.string.live_update_chip_write)
        assertThat(LivePresentation("answering", "search", agents = 3).chip).isEqualTo(R.string.live_update_chip_agents)
        assertThat(LivePresentation("approval_waiting").chip).isEqualTo(R.string.live_update_chip_review)
        assertThat(LivePresentation("input_requested").chip).isEqualTo(R.string.live_update_chip_reply)
        for (phase in listOf("answered", "failed", "stopped")) assertThat(LivePresentation(phase).chip).isNull()
        // A phase this build doesn't know claims no work, whatever its step.
        assertThat(LivePresentation("future_state", "run").chip).isNull()
        assertThat(LivePresentation("future_state", "run").mark).isEqualTo(LivePresentation.Mark.FLAT)
    }

    @Test
    fun `the template's road is time spent per step, never a share of a whole`() {
        assertThat(LivePresentation("answering", "answer").stretches).isEmpty()
        val road = LivePresentation("answering", "search", trail = "srR", pace = "142").stretches
        assertThat(road).containsExactly(
            LivePresentation.Stretch(6, false),
            LivePresentation.Stretch(29, false),
            LivePresentation.Stretch(13, true)
        ).inOrder()
        // An older server sends only a count: even stretches.
        assertThat(LivePresentation("answering", "search", steps = 3).stretches.map { it.length }.distinct()).hasSize(1)
    }

    @Test
    fun `a long road joins its oldest steps into the first stretch, keeping its whole time`() {
        val trail = "s".repeat(14)
        val pace = "1".repeat(5) + "4".repeat(9)
        val shown = LivePresentation("answering", "search", trail = trail, pace = pace)
        val road = shown.stretches
        assertThat(road).hasSize(10)
        assertThat(road.first()).isEqualTo(LivePresentation.Stretch(5 * 6, false))
        assertThat(road.sumOf { it.length }).isEqualTo(shown.ticks.sumOf { it.height.toInt() })
    }
}
