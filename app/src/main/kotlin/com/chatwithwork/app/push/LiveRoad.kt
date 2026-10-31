package com.chatwithwork.app.push

import androidx.annotation.ColorInt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A Live Update's road as Android's ProgressStyle draws it. The bar is a fixed
 * 100 long and the bot only moves as the trail grows, then stays: nothing here
 * knows how far the answer has to go, and only a finished answer has an end.
 *
 * API 37 joins every segment into a default-coloured one past [MAX_SEGMENTS]
 * and drops points past [MAX_POINTS] (`Notification.ProgressStyle`), and a
 * segment can't get narrower than 16dp.
 */
data class LiveRoad(val progress: Int, val indeterminate: Boolean, val segments: List<Segment>, val stops: List<Stop>) {
    data class Segment(val length: Int, @param:ColorInt val color: Int)

    data class Stop(val position: Int, @param:ColorInt val color: Int)

    data class Colors(
        @param:ColorInt val live: Int,
        @param:ColorInt val ahead: Int,
        @param:ColorInt val walked: Int,
        @param:ColorInt val attention: Int,
        @param:ColorInt val positive: Int,
        @param:ColorInt val negative: Int
    )

    companion object {
        const val MAX_SEGMENTS = 10
        const val MAX_POINTS = 4
        private const val LENGTH = 100
        private const val FINISHED_AT = 90
        private const val FURTHEST = 72
        private const val ORIGIN = 12
        private const val PER_STEP = 4
        private const val SLICE = 6f
        private const val FIRST_STOP = 0.18f
        private const val LAST_STOP = 0.86f
        private const val LONE_STOP = 0.52f

        /**
         * Slices that fit beside so many stops. Each stop splits a slice and
         * needs its own room, and past this a 360dp phone falls back to one
         * default-coloured bar (checked on API 37; 1 and 2 stops leave room
         * only for what [MAX_SEGMENTS] allows).
         */
        private val SLICES_BESIDE_STOPS = listOf(9, 8, 7, 6, 4)
        private val RAINBOW =
            listOf(0xFF44FF9A, 0xFF44B0FF, 0xFF8B44FF, 0xFFFF6644, 0xFFEBFF70).map { it.toInt() }

        fun of(shown: LivePresentation, colors: Colors): LiveRoad {
            val state = shown.state
            val working = state == LivePresentation.State.WORKING || state == LivePresentation.State.AGENTS
            if (state == LivePresentation.State.STARTING || (working && shown.walked == 0)) {
                return LiveRoad(0, true, listOf(Segment(LENGTH, colors.live)), emptyList())
            }
            val progress = place(shown)
            val stops = stops(shown, progress)
            val slices = (progress / SLICE).roundToInt().coerceIn(1, SLICES_BESIDE_STOPS[stops.size])
            val trail =
                (0 until slices).map { index ->
                    val length = progress * (index + 1) / slices - progress * index / slices
                    Segment(
                        length,
                        when {
                            working -> rainbow(if (slices > 1) index / (slices - 1f) else 0f)
                            state == LivePresentation.State.FINISHED -> colors.positive
                            else -> colors.walked
                        }
                    )
                }
            val ahead = when (state) {
                LivePresentation.State.DECISION, LivePresentation.State.INPUT -> colors.attention
                LivePresentation.State.FAILED -> colors.negative
                LivePresentation.State.FINISHED -> colors.positive
                else -> colors.ahead
            }
            return LiveRoad(progress, false, trail + Segment(LENGTH - progress, ahead), stops)
        }

        private fun place(shown: LivePresentation): Int = if (shown.state == LivePresentation.State.FINISHED) {
            FINISHED_AT
        } else {
            min(FURTHEST, ORIGIN + shown.walked * PER_STEP)
        }

        /** The newest services, spread along the road already laid. */
        private fun stops(shown: LivePresentation, progress: Int): List<Stop> {
            val brands = shown.services.mapNotNull { LiveServices.of(it)?.brand }.takeLast(MAX_POINTS)
            return brands.mapIndexed { index, brand ->
                val along = if (brands.size == 1) {
                    LONE_STOP
                } else {
                    FIRST_STOP + (LAST_STOP - FIRST_STOP) * index / (brands.size - 1)
                }
                Stop((progress * along).roundToInt(), brand)
            }
        }

        private fun blend(from: Int, to: Int, at: Float): Int = listOf(16, 8, 0).fold(0xFF shl 24) { color, shift ->
            val start = from shr shift and 0xFF
            val end = to shr shift and 0xFF
            color or ((start + (end - start) * at).roundToInt() shl shift)
        }

        /** [at] from 0 to 1 along the app's rainbow. */
        @ColorInt
        fun rainbow(at: Float): Int {
            val place = at.coerceIn(0f, 1f) * RAINBOW.lastIndex
            val index = place.toInt().coerceAtMost(RAINBOW.lastIndex - 1)
            return blend(RAINBOW[index], RAINBOW[index + 1], place - index)
        }
    }
}
