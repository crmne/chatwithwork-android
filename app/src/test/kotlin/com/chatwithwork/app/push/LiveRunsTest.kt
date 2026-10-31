package com.chatwithwork.app.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** docs/server-contract.md, "Live Updates": which messages a chat's card takes, across runs and restarts. */
class LiveRunsTest {
    private val thread = "chat-482139075-42"
    private val store = mutableMapOf<String, String>()
    private val runs = fresh()

    /** A new process reads what the last one kept. */
    private fun fresh() = LiveRuns({ store[it] }, { key, value -> store[key] = value })

    private fun ref(id: String, startedAt: Long? = 1_000, revision: Long = 1) = LiveRunRef(id, startedAt, revision)

    private fun message(run: String?, revision: Long, sentAt: Long = revision * 10, phase: String = "answering") =
        LiveProgress(
            url = "https://chatwithwork.com/482139075/chats/42",
            thread = thread,
            phase = phase,
            message = null,
            step = null,
            steps = 0,
            sentAt = sentAt,
            run = run,
            revision = revision
        )

    private fun admit(message: LiveProgress, showing: Boolean = false, on: LiveRuns = runs) =
        on.admit(thread, message.run!!, message, showing)

    @Test
    fun `A's late end stays out of B's card, even before B's first step`() {
        runs.starting(thread, ref("a"), 500)
        assertThat(admit(message("a", 5))).isEqualTo(LiveRuns.Admission.SHOW)

        runs.starting(thread, ref("b", revision = 9), 600)

        val late = message("a", 12, sentAt = 99_999, phase = "failed")
        assertThat(admit(late)).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(runs.record(thread)?.run).isEqualTo("b")
        assertThat(runs.record(thread)?.ended).isFalse()
    }

    @Test
    fun `within a run only a newer revision counts, whatever the clock says`() {
        runs.starting(thread, ref("a"), 500)

        assertThat(admit(message("a", 5, sentAt = 500))).isEqualTo(LiveRuns.Admission.SHOW)
        assertThat(admit(message("a", 3, sentAt = 900))).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(admit(message("a", 6, sentAt = 100))).isEqualTo(LiveRuns.Admission.SHOW)
    }

    @Test
    fun `at one revision the newer step counts, and a duplicate doesn't`() {
        runs.starting(thread, ref("a"), 500)

        assertThat(admit(message("a", 5, sentAt = 500))).isEqualTo(LiveRuns.Admission.SHOW)
        assertThat(admit(message("a", 5, sentAt = 500))).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(admit(message("a", 5, sentAt = 400))).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(admit(message("a", 5, sentAt = 600))).isEqualTo(LiveRuns.Admission.SHOW)
    }

    @Test
    fun `nothing at an end's revision follows it`() {
        runs.starting(thread, ref("a"), 500)
        assertThat(admit(message("a", 5, sentAt = 500, phase = "answered"))).isEqualTo(LiveRuns.Admission.SHOW)

        assertThat(admit(message("a", 5, sentAt = 600))).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(admit(message("a", 5, sentAt = 700, phase = "answered"))).isEqualTo(LiveRuns.Admission.DROP)
    }

    @Test
    fun `an approval's end from before it was approved can't end the run that resumed`() {
        runs.starting(thread, ref("a"), 500)
        assertThat(admit(message("a", 5, phase = "approval_waiting"))).isEqualTo(LiveRuns.Admission.SHOW)

        assertThat(runs.starting(thread, ref("a", revision = 6), 900)).isEqualTo(1_000)
        assertThat(admit(message("a", 7))).isEqualTo(LiveRuns.Admission.SHOW)

        assertThat(admit(message("a", 5, sentAt = 9_000, phase = "approval_waiting"))).isEqualTo(LiveRuns.Admission.DROP)
    }

    @Test
    fun `a wait isn't an end, so a heartbeat at its revision and the steps after it still count`() {
        runs.starting(thread, ref("a"), 500)
        assertThat(admit(message("a", 5, sentAt = 500, phase = "approval_waiting"))).isEqualTo(LiveRuns.Admission.SHOW)
        assertThat(runs.record(thread)?.ended).isFalse()

        val heartbeat = message("a", 5, sentAt = 600, phase = "approval_waiting")
        assertThat(admit(heartbeat)).isEqualTo(LiveRuns.Admission.SHOW)
        assertThat(admit(message("a", 6))).isEqualTo(LiveRuns.Admission.SHOW)
    }

    @Test
    fun `showing the same run again at an older revision keeps what it heard`() {
        runs.starting(thread, ref("a"), 500)
        admit(message("a", 8, phase = "answered"))

        runs.starting(thread, ref("a", revision = 2), 900)

        assertThat(runs.record(thread)?.revision).isEqualTo(8)
        assertThat(runs.record(thread)?.ended).isTrue()
    }

    @Test
    fun `the timer never restarts within a run, across cards or a new process`() {
        assertThat(runs.starting(thread, ref("a", startedAt = 1_000), 50_000)).isEqualTo(1_000)
        assertThat(runs.starting(thread, ref("a", startedAt = 1_000), 90_000)).isEqualTo(1_000)
        assertThat(fresh().starting(thread, ref("a", startedAt = null), 120_000)).isEqualTo(1_000)
    }

    @Test
    fun `a new run gets its own start`() {
        runs.starting(thread, ref("a", startedAt = 1_000), 50_000)

        assertThat(runs.starting(thread, ref("b", startedAt = 70_000), 80_000)).isEqualTo(70_000)
    }

    @Test
    fun `a run with no start from the server counts from when it first shows, and keeps it`() {
        assertThat(runs.starting(thread, ref("a", startedAt = null), 50_000)).isEqualTo(50_000)
        assertThat(runs.starting(thread, ref("a", startedAt = null), 90_000)).isEqualTo(50_000)
    }

    @Test
    fun `a dismissal holds for the run, also after a new process, and a later run still shows`() {
        runs.starting(thread, ref("a"), 500)
        runs.dismissed(thread, "a", postedAt = 500, now = 600, timeout = 1_000)

        assertThat(fresh().starting(thread, ref("a", revision = 3), 900)).isNull()
        assertThat(admit(message("a", 5), on = fresh())).isEqualTo(LiveRuns.Admission.DROP)
        assertThat(runs.record(thread)?.revision).isEqualTo(5)

        assertThat(runs.starting(thread, ref("b"), 900)).isEqualTo(1_000)
        assertThat(runs.record(thread)?.dismissed).isFalse()
    }

    @Test
    fun `a card timing out is no dismissal`() {
        runs.starting(thread, ref("a"), 500)

        runs.dismissed(thread, "a", postedAt = 500, now = 1_500, timeout = 1_000)
        assertThat(runs.record(thread)?.dismissed).isFalse()

        runs.dismissed(thread, "a", postedAt = 500, now = 1_499, timeout = 1_000)
        assertThat(runs.record(thread)?.dismissed).isTrue()
    }

    @Test
    fun `the app hiding its own card sends no dismissal, so the run shows again`() {
        runs.starting(thread, ref("a"), 500)

        assertThat(runs.starting(thread, ref("a"), 900)).isEqualTo(1_000)
    }

    @Test
    fun `dismissing an older run's card says nothing about the run after it`() {
        runs.starting(thread, ref("b"), 500)

        runs.dismissed(thread, "a", postedAt = 500, now = 600, timeout = 1_000)

        assertThat(runs.record(thread)?.dismissed).isFalse()
    }

    @Test
    fun `after signing out, a late dismissal or push leaves nothing behind`() {
        runs.starting(thread, ref("a"), 500)
        store.clear()

        runs.dismissed(thread, "a", postedAt = 500, now = 600, timeout = 1_000)
        assertThat(admit(message("a", 9))).isEqualTo(LiveRuns.Admission.DROP)

        assertThat(store).isEmpty()
        assertThat(runs.starting(thread, ref("a"), 900)).isEqualTo(1_000)
    }

    @Test
    fun `a card from before runs adopts the first run it hears`() {
        val first = message("a", 4).copy(startedAt = 2_000)

        assertThat(admit(first, showing = true)).isEqualTo(LiveRuns.Admission.SHOW)

        assertThat(runs.record(thread)?.startedAt).isEqualTo(2_000)
        assertThat(admit(message("b", 9))).isEqualTo(LiveRuns.Admission.DROP)
    }

    @Test
    fun `an unreadable record counts as none`() {
        store["run:$thread"] = "not json"

        assertThat(runs.record(thread)).isNull()
        assertThat(runs.starting(thread, ref("a"), 500)).isEqualTo(1_000)
    }
}
