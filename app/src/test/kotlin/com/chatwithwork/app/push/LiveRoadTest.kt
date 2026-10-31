package com.chatwithwork.app.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveRoadTest {
    private val colors = LiveRoad.Colors(1, 2, 3, 4, 5, 6)
    private val working = LivePresentation("answering", "search")

    private fun road(shown: LivePresentation) = LiveRoad.of(shown, colors)

    @Test
    fun `the bar is always the same length, within what Android draws`() {
        val states = listOf("answering", "approval_waiting", "input_requested", "answered", "failed", "stopped")
        for (phase in states) {
            for (steps in listOf(0, 1, 2, 5, 15, 47, 300)) {
                val road = road(LivePresentation(phase, "read", steps))
                assertThat(road.segments.sumOf { it.length }).isEqualTo(100)
                assertThat(road.segments.size).isAtMost(LiveRoad.MAX_SEGMENTS)
                assertThat(road.segments.all { it.length > 0 }).isTrue()
            }
        }
    }

    @Test
    fun `the bot moves only as the trail grows, then stays`() {
        assertThat(road(working.copy(steps = 1)).progress).isEqualTo(16)
        assertThat(road(working.copy(steps = 5)).progress).isEqualTo(32)
        assertThat(road(working.copy(steps = 15)).progress).isEqualTo(72)
        assertThat(road(working.copy(steps = 300)).progress).isEqualTo(72)
        assertThat(road(working.copy(steps = 5, phase = "approval_waiting")).progress).isEqualTo(32)
        assertThat(road(LivePresentation("answered", steps = 2)).progress).isEqualTo(90)
    }

    @Test
    fun `an end message's lost count comes back from the trail`() {
        assertThat(road(LivePresentation("stopped", trail = "srrs")).progress).isEqualTo(28)
    }

    @Test
    fun `nothing known yet is an indeterminate bar in live blue`() {
        val starting = road(LivePresentation("answering", "answer"))
        assertThat(starting.indeterminate).isTrue()
        assertThat(starting.segments).containsExactly(LiveRoad.Segment(100, colors.live))
        assertThat(road(LivePresentation("answering")).indeterminate).isTrue()
        assertThat(road(working.copy(steps = 1)).indeterminate).isFalse()
    }

    @Test
    fun `the colour says who it waits on, and how it ended`() {
        fun ahead(phase: String) = road(LivePresentation(phase, steps = 9)).segments.last().color
        fun walked(phase: String) = road(LivePresentation(phase, steps = 9)).segments.first().color
        assertThat(ahead("answering")).isEqualTo(colors.ahead)
        assertThat(ahead("approval_waiting")).isEqualTo(colors.attention)
        assertThat(ahead("input_requested")).isEqualTo(colors.attention)
        assertThat(ahead("answered")).isEqualTo(colors.positive)
        assertThat(ahead("failed")).isEqualTo(colors.negative)
        assertThat(ahead("stopped")).isEqualTo(colors.ahead)
        assertThat(walked("answered")).isEqualTo(colors.positive)
        for (phase in listOf("approval_waiting", "input_requested", "failed", "stopped")) {
            assertThat(walked(phase)).isEqualTo(colors.walked)
        }
    }

    @Test
    fun `working lays the whole rainbow up to the bot`() {
        val trail = road(working.copy(steps = 15)).segments.dropLast(1)
        assertThat(trail.first().color).isEqualTo(0xFF44FF9A.toInt())
        assertThat(trail.last().color).isEqualTo(0xFFEBFF70.toInt())
        assertThat(trail.map { it.color }.toSet().size).isEqualTo(trail.size)
    }

    @Test
    fun `stops are the newest known services, spread along the road laid`() {
        val services = listOf("google_drive", "slack", "todoist", "hubspot", "someones_server", "linear")
        val stops = road(working.copy(steps = 15, services = services)).stops
        assertThat(stops.size).isEqualTo(LiveRoad.MAX_POINTS)
        assertThat(stops.first().color).isEqualTo(LiveServices.of("slack")?.brand)
        assertThat(stops.last().color).isEqualTo(LiveServices.of("linear")?.brand)
        assertThat(stops.map { it.position }).isInOrder()
        assertThat(stops.last().position).isLessThan(72)

        assertThat(road(working.copy(steps = 15, services = listOf("slack"))).stops.single().position).isEqualTo(37)
    }

    @Test
    fun `stops take the place of slices, so the parts always fit`() {
        val four = listOf("slack", "google_drive", "todoist", "hubspot")
        for (stops in 0..4) {
            val road = road(working.copy(steps = 47, services = four.take(stops)))
            assertThat(road.stops).hasSize(stops)
            assertThat(road.segments.size).isAtMost(LiveRoad.MAX_SEGMENTS - stops)
        }
    }

    @Test
    fun `an older server's road has no stops`() {
        val road = road(LivePresentation("answering", "search", 7))
        assertThat(road.stops).isEmpty()
        assertThat(road.progress).isEqualTo(40)
    }
}
