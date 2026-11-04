package com.chatwithwork.app.sidebar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.ColorInt
import kotlin.random.Random

/**
 * The bots' mark, as the web draws it (BotMark in the Rails app): our pixel
 * chat bubble with two tall eyes, one 16-square drawing scaled smoothly to
 * any size, so it reads like an icon in a row and like pixel art when large.
 * Each frame is one anti-aliased path merged into a single outline, so smooth
 * edges leave no seams between squares.
 *
 * It blinks now and then: for a tenth of a second every 4 to 9 seconds while
 * it's shown it draws its closed frame, the same drawing with each eye only
 * its bottom row, never with animations turned off (Remove animations, or an
 * animator scale of 0), nor while [blinks] is false (a paused bot holds still).
 *
 * With a [face] it has other eyes in place of the blink; a face of two frames
 * (thinking) swaps them every 1.6 seconds, as the web's CSS does, and holds
 * its first under the same conditions. With a [paper] it's a bot's sticker
 * (`bot_mark colour:` on the web): the whole bubble filled with that colour
 * and the outline and eyes in black, the same in both themes.
 */
class BotMarkDrawable(
    context: Context,
    @ColorInt color: Int,
    sizeDp: Float = 20f,
    private val blinks: Boolean = true,
    /** Other eyes in place of its own, frame by frame, as `[x, y, width, height]` in the grid ([FACES]). */
    private val face: List<List<IntArray>>? = null,
    /** The bubble's colour for a sticker, whose lines are then black; null draws the lines alone in [color]. */
    @ColorInt private val paper: Int? = null
) : Drawable(),
    Runnable {
    private val sizePx = Math.round(sizeDp * context.resources.displayMetrics.density)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (paper != null) Color.BLACK else color }
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = paper ?: Color.TRANSPARENT }
    private val swaps = face != null && face.size > 1
    private var blinking = false
    private var framesFor: Rect? = null
    private var open = Path()
    private var closed = Path()
    private var bubble = Path()

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        if (framesFor != bounds) {
            val left = bounds.left + (bounds.width() - side) / 2f
            val top = bounds.top + (bounds.height() - side) / 2f
            val cell = side / GRID.size
            open = if (face != null) faceFrame(left, top, cell, face.first()) else frame(left, top, cell, "#Ee")
            closed = if (swaps) faceFrame(left, top, cell, face!!.last()) else frame(left, top, cell, "#e")
            // The paper runs under the lines too, so smooth scaling leaves no gap between them.
            bubble = if (paper != null) frame(left, top, cell, "#Eeo") else Path()
            framesFor = Rect(bounds)
        }
        if (paper != null) canvas.drawPath(bubble, paperPaint)
        canvas.drawPath(if (blinking) closed else open, paint)
    }

    private fun frame(left: Float, top: Float, cell: Float, kinds: String) = Path().also { path ->
        GRID.forEachIndexed { y, row ->
            row.forEachIndexed { x, square ->
                if (square in kinds) {
                    val cellPath = Path().apply {
                        addRect(
                            left + x * cell,
                            top + y * cell,
                            left + (x + 1) * cell,
                            top + (y + 1) * cell,
                            Path.Direction.CW
                        )
                    }
                    path.op(cellPath, Path.Op.UNION)
                }
            }
        }
    }

    private fun faceFrame(left: Float, top: Float, cell: Float, eyes: List<IntArray>) =
        frame(left, top, cell, "#").also { path ->
            eyes.forEach { (x, y, width, height) ->
                val eye = Path().apply {
                    addRect(
                        left + x * cell,
                        top + y * cell,
                        left + (x + width) * cell,
                        top + (y + height) * cell,
                        Path.Direction.CW
                    )
                }
                path.op(eye, Path.Op.UNION)
            }
        }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        unscheduleSelf(this)
        blinking = false
        if (visible && moves && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(this, SystemClock.uptimeMillis() + nextChange())
        }
        return changed
    }

    /** A face of two frames swaps them; a single face is still; otherwise it blinks unless told not to. */
    private val moves get() = if (face != null) swaps else blinks

    override fun run() {
        blinking = !blinking
        invalidateSelf()
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(this, SystemClock.uptimeMillis() + nextChange())
        } else {
            blinking = false
        }
    }

    private fun nextChange() = when {
        swaps -> LOOK_MS
        blinking -> CLOSED_MS
        else -> nextBlink()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun nextBlink() = Random.nextLong(4_000, 9_001)

    companion object {
        /**
         * The drawing: `#` the outline, `o` the inside, `E` eye squares of the
         * open frame only, `e` of both frames.
         */
        val GRID = listOf(
            "................",
            "................",
            ".##############.",
            "#oooooooooooooo#",
            "#oooooooooooooo#",
            "#oooEEooooEEooo#",
            "#oooEEooooEEooo#",
            "#oooeeooooeeooo#",
            "#oooooooooooooo#",
            "#oooooooooooooo#",
            "#oooooooooooooo#",
            ".###ooo########.",
            "...#oo#.........",
            "...#o#..........",
            "...##...........",
            "................"
        )

        private fun eyes(vararg squares: Int) = squares.toList().chunked(4).map { it.toIntArray() }

        /**
         * The web's faces (BotMark::FACES), by the names the sidebar's JSON
         * gives, each its frames' eyes: thinking looks up and about, writing
         * reads along a line, waiting looks at you, wide-eyed and still, happy
         * smiles with its eyes, and flat is both eyes a level line, for what
         * didn't finish or was stopped. Never crossed-out eyes.
         */
        val FACES: Map<String, List<List<IntArray>>> = mapOf(
            "thinking" to listOf(eyes(5, 4, 2, 3, 11, 4, 2, 3), eyes(3, 4, 2, 3, 9, 4, 2, 3)),
            "writing" to listOf(eyes(5, 7, 2, 2, 11, 7, 2, 2), eyes(3, 7, 2, 2, 9, 7, 2, 2)),
            "waiting" to listOf(
                eyes(3, 4, 3, 1, 3, 5, 1, 1, 5, 5, 1, 1, 3, 6, 3, 2, 10, 4, 3, 1, 10, 5, 1, 1, 12, 5, 1, 1, 10, 6, 3, 2)
            ),
            "happy" to listOf(eyes(3, 7, 1, 1, 4, 6, 2, 1, 6, 7, 1, 1, 9, 7, 1, 1, 10, 6, 2, 1, 12, 7, 1, 1)),
            "flat" to listOf(eyes(3, 7, 3, 1, 10, 7, 3, 1))
        )

        /** The happy face, for nothing left to do. */
        val HAPPY = FACES.getValue("happy")

        private const val CLOSED_MS = 120L

        /** Half the web's 3.2-second look about. */
        private const val LOOK_MS = 1_600L
    }
}
