package com.chatwithwork.app.sidebar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
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
 */
class BotMarkDrawable(context: Context, @ColorInt color: Int, sizeDp: Float = 20f, private val blinks: Boolean = true) :
    Drawable(),
    Runnable {
    private val sizePx = Math.round(sizeDp * context.resources.displayMetrics.density)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private var blinking = false
    private var framesFor: Rect? = null
    private var open = Path()
    private var closed = Path()

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        if (framesFor != bounds) {
            val left = bounds.left + (bounds.width() - side) / 2f
            val top = bounds.top + (bounds.height() - side) / 2f
            open = frame(left, top, side / GRID.size, "#Ee")
            closed = frame(left, top, side / GRID.size, "#e")
            framesFor = Rect(bounds)
        }
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

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        unscheduleSelf(this)
        blinking = false
        if (visible && blinks && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(this, SystemClock.uptimeMillis() + nextBlink())
        }
        return changed
    }

    override fun run() {
        blinking = !blinking
        invalidateSelf()
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(this, SystemClock.uptimeMillis() + if (blinking) CLOSED_MS else nextBlink())
        } else {
            blinking = false
        }
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
        /** The drawing: `#` the outline, `E` eye squares of the open frame only, `e` of both frames. */
        val GRID = listOf(
            "................",
            "................",
            ".##############.",
            "#..............#",
            "#..............#",
            "#...EE....EE...#",
            "#...EE....EE...#",
            "#...ee....ee...#",
            "#..............#",
            "#..............#",
            "#..............#",
            ".###...########.",
            "...#..#.........",
            "...#.#..........",
            "...##...........",
            "................"
        )

        private const val CLOSED_MS = 120L
    }
}
