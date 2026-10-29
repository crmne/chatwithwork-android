package com.chatwithwork.app.composer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * The microphone's level while dictation listens, as the web composer's
 * waveform (components/composer.css, `.chat-prompt__waveform`): neutral bars
 * across the composer, newest on the right, each as tall as the loudness it
 * heard. With animations off (Settings › Accessibility › Remove animations)
 * it's a still meter instead, lit up to the level. Decorative: TalkBack hears
 * "Listening" and the time beside it.
 */
class WaveformView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val bar = BAR_DP * density
    private val gap = GAP_DP * density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        }
    private val box = RectF()
    private var levels = FloatArray(0)
    private var latest = 0f

    /** Whether it's frozen: transcribing, it keeps what it heard, faded. */
    var frozen = false
        set(value) {
            field = value
            alpha = if (value) FROZEN_ALPHA else 1f
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Adds the latest [level] (0 to 1) on the right, moving the rest left. */
    fun add(level: Float) {
        if (frozen) return
        latest = level.coerceIn(0f, 1f)
        if (levels.isNotEmpty()) {
            System.arraycopy(levels, 1, levels, 0, levels.size - 1)
            levels[levels.size - 1] = latest
        }
        invalidate()
    }

    fun reset() {
        levels.fill(0f)
        latest = 0f
        frozen = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val count = ((w + gap) / (bar + gap)).toInt().coerceAtLeast(1)
        if (count == levels.size) return
        // Keep the newest on the right when the width changes (rotation, a tablet's split screen).
        val resized = FloatArray(count)
        val keep = minOf(count, levels.size)
        System.arraycopy(levels, levels.size - keep, resized, count - keep, keep)
        levels = resized
    }

    override fun onDraw(canvas: Canvas) {
        if (levels.isEmpty()) return
        val still = !ValueAnimator.areAnimatorsEnabled()
        // Spread the bars so the row spans the whole width, as justify-content: space-between does.
        val step = if (levels.size > 1) (width - bar) / (levels.size - 1) else 0f
        val radius = bar / 2f
        val middle = height / 2f
        levels.forEachIndexed { index, level ->
            val left = index * step
            if (still) {
                // A still meter: every bar half tall, lit up to the latest level from the left.
                val lit = index < Math.round(latest * levels.size)
                paint.alpha = if (lit) 255 else STILL_UNLIT_ALPHA
                box.set(left, middle - height / 4f, left + bar, middle + height / 4f)
            } else {
                paint.alpha = 255
                val half = height * (MIN_HEIGHT + (1 - MIN_HEIGHT) * level) / 2f
                box.set(left, middle - half, left + bar, middle + half)
            }
            canvas.drawRoundRect(box, radius, radius, paint)
        }
    }

    private companion object {
        const val BAR_DP = 3f
        const val GAP_DP = 2f
        const val MIN_HEIGHT = 0.08f
        const val FROZEN_ALPHA = 0.4f
        const val STILL_UNLIT_ALPHA = 64
    }
}
