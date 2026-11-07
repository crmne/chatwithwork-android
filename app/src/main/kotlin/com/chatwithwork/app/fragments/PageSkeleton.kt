package com.chatwithwork.app.fragments

import com.chatwithwork.app.fragments.ChatSkeleton.Bone

/**
 * The shape of every page before it paints, by kind: the chat's own
 * (ChatSkeleton) and the rest, drawn with the same bones and shimmer
 * (SkeletonView). The server picks the kind with its path configuration's
 * `skeleton` property. The numbers are the same on iOS. All in pixels,
 * top-left origin.
 */
object PageSkeleton {
    enum class Kind { CHAT, NEW_CHAT, LIST, SETTINGS, FORM, PAGE }

    /**
     * Not a bone: a group's box, filled with the surface under a hairline,
     * like a settings group. [dividers] are where hairlines split its rows.
     */
    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val dividers: List<Float> = emptyList()
    )

    /** What one screen draws: boxes under bones, and the composer's place where the page has one. */
    data class Sketch(val bones: List<Bone>, val boxes: List<Box> = emptyList(), val composer: Bone? = null)

    /**
     * The kind for a screen at [location] whose path configuration says
     * [skeleton]: "none" for no skeleton, an unknown value draws a page. A
     * server that doesn't say (one from before 2026-11-07) gets a chat's for
     * a chat and nothing elsewhere, as before.
     */
    fun kind(location: String, skeleton: String?): Kind? = when (skeleton) {
        null -> if (ChatSkeleton.shows(location)) Kind.CHAT else null
        NONE -> null
        else -> Kind.entries.firstOrNull { it.name.lowercase() == skeleton } ?: Kind.PAGE
    }

    /**
     * The [kind]'s sketch for a [width] by [height] screen whose bar ends at
     * [top] and whose system bars cover it from [bottom] down.
     */
    fun sketch(
        kind: Kind,
        width: Float,
        height: Float,
        top: Float,
        bottom: Float,
        density: Float,
        rtl: Boolean
    ): Sketch {
        if (kind == Kind.CHAT) {
            val bones = ChatSkeleton.body(width, height, top, density, rtl)
            return Sketch(bones.dropLast(1), composer = bones.last())
        }
        val frame = Frame(width, height, top, bottom, density)
        val sketch =
            when (kind) {
                Kind.NEW_CHAT -> newChat(frame)
                Kind.LIST -> list(frame)
                Kind.SETTINGS -> settings(frame)
                Kind.FORM -> form(frame)
                else -> page(frame)
            }
        return if (rtl) sketch.mirrored(width) else sketch
    }

    /** The screen as a column: 16 at the sides, no wider than a chat's, centred. */
    private class Frame(val width: Float, val height: Float, val top: Float, val bottom: Float, val density: Float) {
        fun dp(value: Float) = value * density

        val margin = dp(MARGIN_DP)
        val columnWidth = minOf(width - 2 * margin, dp(COLUMN_MAX_DP))
        val start = (width - columnWidth) / 2
        val end = start + columnWidth

        /** Where the first bone may start: below the bar. */
        val first = top + dp(TOP_GAP_DP)

        fun line(left: Float, top: Float, lineWidth: Float, heightDp: Float): Bone {
            val h = dp(heightDp)
            return Bone(left, top, left + lineWidth, top + h, List(4) { h / 2 })
        }

        fun centred(top: Float, share: Float, heightDp: Float): Bone {
            val lineWidth = columnWidth * share
            return line(start + (columnWidth - lineWidth) / 2, top, lineWidth, heightDp)
        }
    }

    /** The greeting and three suggestions over the composer at rest, as New chat opens. */
    private fun newChat(f: Frame): Sketch {
        val composerBottom = f.height - f.dp(COMPOSER_BOTTOM_DP)
        val composerTop = composerBottom - f.dp(COMPOSER_HEIGHT_DP)
        val composer =
            Bone(f.margin, composerTop, f.width - f.margin, composerBottom, List(4) { f.dp(COMPOSER_RADIUS_DP) })

        val greetingCentre = f.top + (composerTop - f.top) * GREETING_AT
        val greetingTop = greetingCentre - f.dp(GREETING_HEIGHT_DP) / 2
        val bones = mutableListOf(f.centred(greetingTop, GREETING_SHARE, GREETING_HEIGHT_DP))
        var y = greetingTop + f.dp(GREETING_HEIGHT_DP) + f.dp(GREETING_TO_PILLS_DP)
        PILL_SHARES.forEach { share ->
            bones += f.centred(y, share, PILL_HEIGHT_DP)
            y += f.dp(PILL_HEIGHT_DP) + f.dp(PILL_GAP_DP)
        }
        val limit = composerTop - f.dp(COMPOSER_CLEARANCE_DP)
        return Sketch(bones.filter { it.top >= f.top && it.bottom <= limit }, composer = composer)
    }

    /** Rows of a mark and two lines, as many as fit: Decisions, Bots, chats, projects. */
    private fun list(f: Frame): Sketch {
        val bones = mutableListOf<Bone>()
        val row = f.dp(ROW_HEIGHT_DP)
        val mark = f.dp(MARK_DP)
        val textStart = f.start + mark + f.dp(MARK_TO_TEXT_DP)
        val textWidth = f.end - textStart
        var y = f.first
        var index = 0
        while (y + row <= f.bottom) {
            val markTop = y + (row - mark) / 2
            bones += Bone(f.start, markTop, f.start + mark, markTop + mark, List(4) { mark / 2 })
            bones += f.line(textStart, y + f.dp(FIRST_LINE_TOP_DP), textWidth * cycle(FIRST_LINE_SHARES, index), 12f)
            bones += f.line(textStart, y + f.dp(SECOND_LINE_TOP_DP), textWidth * cycle(SECOND_LINE_SHARES, index), 10f)
            y += row
            index++
        }
        return Sketch(bones)
    }

    /** Two sections, each a heading, a muted line, and a group of rows: Settings and its tabs. */
    private fun settings(f: Frame): Sketch {
        val bones = mutableListOf<Bone>()
        val boxes = mutableListOf<Box>()
        var y = f.first
        SECTION_ROWS.forEachIndexed { section, rows ->
            if (section > 0) y += f.dp(SECTION_GAP_DP)
            val heading = f.line(f.start, y, f.columnWidth * HEADING_SHARE, 14f)
            y = heading.bottom + f.dp(10f)
            val muted = f.line(f.start, y, f.columnWidth * MUTED_SHARE, 10f)
            y = muted.bottom + f.dp(14f)
            listOf(heading, muted).filterTo(bones) { it.bottom <= f.bottom }
            y = group(f, y, rows, bones, boxes)
        }
        return Sketch(bones, boxes)
    }

    /** Four labelled fields: every sheet that makes or changes something. */
    private fun form(f: Frame): Sketch {
        val bones = mutableListOf<Bone>()
        val boxes = mutableListOf<Box>()
        var y = f.first
        repeat(FIELDS) { field ->
            if (field > 0) y += f.dp(FIELD_GAP_DP)
            val label = f.line(f.start, y, f.columnWidth * cycle(LABEL_SHARES, field), 10f)
            val boxTop = label.bottom + f.dp(8f)
            val box = Box(f.start, boxTop, f.end, boxTop + f.dp(FIELD_HEIGHT_DP), f.dp(BOX_RADIUS_DP))
            if (box.bottom > f.bottom) return Sketch(bones, boxes)
            bones += label
            boxes += box
            y = box.bottom
        }
        return Sketch(bones, boxes)
    }

    /** A heading, two lines of text, and a group: a bot, a project, the panel beside a chat. */
    private fun page(f: Frame): Sketch {
        val bones = mutableListOf<Bone>()
        val boxes = mutableListOf<Box>()
        val heading = f.line(f.start, f.first, f.columnWidth * PAGE_HEADING_SHARE, 20f)
        var y = heading.bottom + f.dp(12f)
        val text =
            PAGE_TEXT_SHARES.map { share ->
                val lineTop = y + (f.dp(LINE_RHYTHM_DP) - f.dp(12f)) / 2
                y += f.dp(LINE_RHYTHM_DP)
                f.line(f.start, lineTop, f.columnWidth * share, 12f)
            }
        (listOf(heading) + text).filterTo(bones) { it.bottom <= f.bottom }
        group(f, y + f.dp(24f), PAGE_ROWS, bones, boxes)
        return Sketch(bones, boxes)
    }

    /**
     * A group's box at [top] holding [rows] rows that fit, each a label at its
     * start and a short value at its end, split by hairlines. Returns its bottom.
     */
    private fun group(f: Frame, top: Float, rows: Int, bones: MutableList<Bone>, boxes: MutableList<Box>): Float {
        val row = f.dp(GROUP_ROW_DP)
        val inset = f.dp(GROUP_INSET_DP)
        val inner = f.columnWidth - 2 * inset
        val fitting = (0 until rows).takeWhile { top + (it + 1) * row <= f.bottom }
        if (fitting.isEmpty()) return top
        fitting.forEach { index ->
            val centre = top + index * row + row / 2
            val boneTop = centre - f.dp(6f)
            bones += f.line(f.start + inset, boneTop, inner * cycle(GROUP_LABEL_SHARES, index), 12f)
            val valueWidth = inner * VALUE_SHARE
            bones += f.line(f.end - inset - valueWidth, boneTop, valueWidth, 12f)
        }
        val bottom = top + fitting.size * row
        boxes += Box(f.start, top, f.end, bottom, f.dp(BOX_RADIUS_DP), fitting.drop(1).map { top + it * row })
        return bottom
    }

    private fun cycle(shares: List<Float>, index: Int) = shares[index % shares.size]

    /** Right to left: the same sketch seen in a mirror, corners and all. */
    private fun Sketch.mirrored(width: Float) = Sketch(
        bones.map { it.mirrored(width) },
        boxes.map { it.copy(left = width - it.right, right = width - it.left) },
        composer?.mirrored(width)
    )

    private fun Bone.mirrored(width: Float) =
        Bone(width - right, top, width - left, bottom, listOf(radii[1], radii[0], radii[3], radii[2]))

    const val NONE = "none"

    private const val MARGIN_DP = 16f
    private const val COLUMN_MAX_DP = 768f
    private const val TOP_GAP_DP = 16f
    private const val LINE_RHYTHM_DP = 25.6f
    private const val BOX_RADIUS_DP = 14f

    // The chat's composer at rest (ChatSkeleton).
    private const val COMPOSER_BOTTOM_DP = 10f
    private const val COMPOSER_HEIGHT_DP = 100f
    private const val COMPOSER_RADIUS_DP = 20f
    private const val COMPOSER_CLEARANCE_DP = 12f

    private const val GREETING_AT = 0.4f
    private const val GREETING_HEIGHT_DP = 22f
    private const val GREETING_SHARE = 0.6f
    private const val GREETING_TO_PILLS_DP = 20f
    private const val PILL_HEIGHT_DP = 32f
    private const val PILL_GAP_DP = 8f
    private val PILL_SHARES = listOf(0.44f, 0.58f, 0.36f)

    private const val ROW_HEIGHT_DP = 64f
    private const val MARK_DP = 32f
    private const val MARK_TO_TEXT_DP = 12f
    private const val FIRST_LINE_TOP_DP = 16f
    private const val SECOND_LINE_TOP_DP = 38f
    private val FIRST_LINE_SHARES = listOf(0.56f, 0.42f, 0.64f, 0.48f)
    private val SECOND_LINE_SHARES = listOf(0.34f, 0.52f, 0.28f, 0.44f)

    private val SECTION_ROWS = listOf(4, 3)
    private const val SECTION_GAP_DP = 28f
    private const val HEADING_SHARE = 0.36f
    private const val MUTED_SHARE = 0.72f
    private const val GROUP_ROW_DP = 52f
    private const val GROUP_INSET_DP = 16f
    private val GROUP_LABEL_SHARES = listOf(0.44f, 0.56f, 0.38f, 0.5f)
    private const val VALUE_SHARE = 0.14f

    private const val FIELDS = 4
    private const val FIELD_GAP_DP = 20f
    private const val FIELD_HEIGHT_DP = 44f
    private val LABEL_SHARES = listOf(0.3f, 0.22f, 0.36f, 0.26f)

    private const val PAGE_HEADING_SHARE = 0.52f
    private val PAGE_TEXT_SHARES = listOf(1f, 0.68f)
    private const val PAGE_ROWS = 4
}

/** Which skeleton the server's path configuration gives a screen (PageSkeleton.kind). */
val HashMap<String, Any>.skeleton: String?
    get() = get(SKELETON) as? String

const val SKELETON = "skeleton"
