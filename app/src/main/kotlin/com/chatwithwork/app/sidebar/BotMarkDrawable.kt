package com.chatwithwork.app.sidebar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.ColorInt
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

/**
 * The bots' mark, as the web draws it (BotMark in the Rails app): our pixel
 * chat bubble with two tall eyes, from its 20px master. Each master
 * pixel is a whole number of device pixels (2 at xhdpi for 20dp, 3 at
 * xxhdpi), so nothing is resampled.
 *
 * It blinks now and then: for a tenth of a second every 4 to 9 seconds while
 * it's shown it draws its closed frame, the same grid with each eye only its
 * bottom row, as whole squares like the open one, never with animations turned
 * off (Remove animations, or an animator scale of 0).
 */
class BotMarkDrawable(
    context: Context,
    @ColorInt color: Int,
    sizeDp: Float = 20f
) : Drawable(), Runnable {
    private val cell = max(1, floor(sizeDp * context.resources.displayMetrics.density / GRID.size).toInt())
    private val paint = Paint().apply { this.color = color }
    private var blinking = false

    override fun getIntrinsicWidth(): Int = cell * GRID.size

    override fun getIntrinsicHeight(): Int = cell * GRID.size

    override fun draw(canvas: Canvas) {
        val left = bounds.left + (bounds.width() - intrinsicWidth) / 2
        val top = bounds.top + (bounds.height() - intrinsicHeight) / 2
        GRID.forEachIndexed { y, row ->
            row.forEachIndexed { x, square ->
                if (square == '#' || square == 'e' || (square == 'E' && !blinking)) {
                    val l = (left + x * cell).toFloat()
                    val t = (top + y * cell).toFloat()
                    canvas.drawRect(l, t, l + cell, t + cell, paint)
                }
            }
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        unscheduleSelf(this)
        blinking = false
        if (visible && ValueAnimator.areAnimatorsEnabled()) {
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
        /** The 20px master: `#` the outline, `E` eye pixels of the open frame only, `e` of both frames. */
        val GRID = listOf(
            "....................",
            "....................",
            ".##################.",
            "#..................#",
            "#..................#",
            "#..................#",
            "#.....EE....EE.....#",
            "#.....EE....EE.....#",
            "#.....EE....EE.....#",
            "#.....ee....ee.....#",
            "#..................#",
            "#..................#",
            "#..................#",
            ".####....##########.",
            "....#...#...........",
            "....#..#............",
            "....#.#.............",
            "....##..............",
            "....................",
            "...................."
        )

        private const val CLOSED_MS = 120L
    }
}
