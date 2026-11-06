package com.chatwithwork.app.fragments

import com.chatwithwork.app.routing.AppUrls

/**
 * The shape of a chat, for the moments before its page paints: what's drawn
 * where and how the shimmer travels, kept apart from the views so it can be
 * tested (ChatSkeletonView draws it). The numbers follow the chat page as the
 * app shows it (Chat with Work's docs/ui/native-apps.md, "Chat screen
 * proportions") and are the same on iOS. All in pixels, top-left origin.
 */
object ChatSkeleton {
    /** One shape to fill. [radii] are its corners: top left, top right, bottom right, bottom left. */
    data class Bone(val left: Float, val top: Float, val right: Float, val bottom: Float, val radii: List<Float>)

    const val SWEEP_MS = 1_400L
    const val FADE_MS = 200L

    /** Past this the skeleton goes whatever the page is doing: it never stays for good. */
    const val TIMEOUT_MS = 10_000L

    /** A chat that exists has a page worth waiting for; New chat has nothing to sketch. */
    fun shows(location: String): Boolean = AppUrls.chatNumber(location) != null

    /** With Remove animations on, the bones stay still. */
    fun sweeps(animatorsEnabled: Boolean): Boolean = animatorsEnabled

    /**
     * Two questions and their answers from [top] down, and the composer at
     * rest at the bottom of [height]. Bones that wouldn't clear the composer
     * (a short screen, the page in landscape) are left out.
     */
    fun body(width: Float, height: Float, top: Float, density: Float, rtl: Boolean): List<Bone> {
        val dp = { value: Float -> value * density }
        val margin = dp(MARGIN_DP)
        val columnWidth = minOf(width - 2 * margin, dp(COLUMN_MAX_DP))
        val columnStart = (width - columnWidth) / 2

        val composerBottom = height - dp(COMPOSER_BOTTOM_DP)
        val composerTop = composerBottom - dp(COMPOSER_HEIGHT_DP)
        val composer =
            Bone(margin, composerTop, width - margin, composerBottom, List(4) { dp(COMPOSER_RADIUS_DP) })

        val bones = mutableListOf<Bone>()
        var y = top + dp(TOP_GAP_DP)

        fun question(share: Float, heightDp: Float) {
            val bubble = columnWidth * share
            val (left, right) =
                if (rtl) {
                    columnStart to columnStart + bubble
                } else {
                    columnStart + columnWidth - bubble to
                        columnStart + columnWidth
                }
            val r = dp(BUBBLE_RADIUS_DP)
            val end = dp(BUBBLE_END_RADIUS_DP)
            // The bubble's tail corner is at its bottom end: right, or left when reading right to left.
            bones += Bone(left, y, right, y + dp(heightDp), if (rtl) listOf(r, r, r, end) else listOf(r, r, end, r))
            y += dp(heightDp) + dp(QUESTION_TO_ANSWER_DP)
        }

        fun answer(lines: Int, lastShare: Float) {
            repeat(lines) { line ->
                val share = if (line == lines - 1) lastShare else 1f
                val lineWidth = columnWidth * share
                val (left, right) =
                    if (rtl) {
                        columnStart + columnWidth - lineWidth to columnStart + columnWidth
                    } else {
                        columnStart to
                            columnStart + lineWidth
                    }
                val lineTop = y + (dp(LINE_RHYTHM_DP) - dp(LINE_HEIGHT_DP)) / 2
                bones += Bone(left, lineTop, right, lineTop + dp(LINE_HEIGHT_DP), List(4) { dp(LINE_HEIGHT_DP) / 2 })
                y += dp(LINE_RHYTHM_DP)
            }
            y += dp(ANSWER_TO_QUESTION_DP)
        }

        question(FIRST_QUESTION_SHARE, FIRST_QUESTION_HEIGHT_DP)
        answer(FIRST_ANSWER_LINES, LAST_LINE_SHARE)
        question(SECOND_QUESTION_SHARE, SECOND_QUESTION_HEIGHT_DP)
        answer(SECOND_ANSWER_LINES, LAST_LINE_SHARE)

        return bones.filter { it.bottom <= composerTop - dp(COMPOSER_CLEARANCE_DP) } + composer
    }

    /** The title's place in the bar, at [start] from the reading edge, until the page names the chat. */
    fun title(width: Float, start: Float, centerY: Float, density: Float, rtl: Boolean): Bone {
        val boneWidth = TITLE_WIDTH_DP * density
        val halfHeight = TITLE_HEIGHT_DP * density / 2
        val left = if (rtl) width - start - boneWidth else start
        return Bone(left, centerY - halfHeight, left + boneWidth, centerY + halfHeight, List(4) { halfHeight })
    }

    /**
     * How far the band's start has travelled from x = 0 at [fraction] of a
     * sweep across a [width] by [height] screen: entirely off screen at both
     * ends, even where its slant carries it furthest. Reading right to left it
     * comes in from the right.
     */
    fun bandOffset(fraction: Float, width: Float, height: Float, rtl: Boolean): Float {
        val band = bandWidth(width)
        val from = -band
        val to = width + height * BAND_SLANT
        val travelled = from + (to - from) * fraction
        return if (rtl) from + to - travelled else travelled
    }

    /** The band is about half a screen wide, soft on both sides. */
    fun bandWidth(width: Float): Float = width * BAND_SHARE

    /** How far sideways the band leans per pixel down: about 20 degrees off upright. */
    const val BAND_SLANT = 0.36f

    private const val BAND_SHARE = 0.5f
    private const val MARGIN_DP = 16f

    // The web's chat column, at its widest (tablets).
    private const val COLUMN_MAX_DP = 768f
    private const val TOP_GAP_DP = 16f

    // .message__bubble: --radius-card, with --radius-tag at the bottom end.
    private const val BUBBLE_RADIUS_DP = 14f
    private const val BUBBLE_END_RADIUS_DP = 6f
    private const val FIRST_QUESTION_SHARE = 0.62f
    private const val FIRST_QUESTION_HEIGHT_DP = 44f
    private const val SECOND_QUESTION_SHARE = 0.45f
    private const val SECOND_QUESTION_HEIGHT_DP = 36f
    private const val QUESTION_TO_ANSWER_DP = 20f
    private const val ANSWER_TO_QUESTION_DP = 24f

    // Answer text is 16 on 25.6: a bone where each line's letters sit.
    private const val LINE_HEIGHT_DP = 12f
    private const val LINE_RHYTHM_DP = 25.6f
    private const val FIRST_ANSWER_LINES = 4
    private const val SECOND_ANSWER_LINES = 3
    private const val LAST_LINE_SHARE = 0.6f

    private const val TITLE_WIDTH_DP = 120f
    private const val TITLE_HEIGHT_DP = 12f

    // view_composer.xml at rest: 10 below, an 8dp padded box around a 40dp field and a 44dp row, RADIUS_DP corners.
    private const val COMPOSER_BOTTOM_DP = 10f
    private const val COMPOSER_HEIGHT_DP = 100f
    private const val COMPOSER_RADIUS_DP = 20f
    private const val COMPOSER_CLEARANCE_DP = 12f
}
