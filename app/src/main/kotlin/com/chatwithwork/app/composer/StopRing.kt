package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

/**
 * Stop's ring while an answer runs, the web's `.chat-prompt__ring` on a touch
 * screen (components/composer.css): a 2dp ring of tokens.css --rainbow-conic,
 * 2dp inside the 44dp target with corners 13dp outside and 11dp inside, so 1dp
 * of nothing parts it from the 34dp square with its 9dp corners (measured in a
 * phone browser). The
 * ring's shape stays put while the rainbow turns inside it ([turn]), once in
 * 2.4 seconds; while the answer stops it greys out at half strength and turns
 * once in 6 (ComposerView runs both). It's Stop's foreground, so it draws over
 * nothing Stop draws.
 */
class StopRing(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    private val ring = RectF()
    private val spin = Matrix()
    private var colors: SweepGradient? = null

    /** The answer is stopping: the web's `grayscale(1)` and `opacity: 0.5` on the ring. */
    var stopping = false
        set(value) {
            if (field == value) return
            field = value
            colors = null
            invalidateSelf()
        }

    /** How far the rainbow has turned, 0 to 1. */
    var turn = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        colors = null
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        // The stroke's middle, 3dp in: the ring runs from 2dp to 4dp inside the target.
        ring.set(bounds)
        ring.inset(3 * density, 3 * density)
        val shader = colors ?: SweepGradient(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            if (stopping) GREYS else RAINBOW,
            null
        ).also { colors = it }
        // CSS's conic gradient starts at the top and Android's sweep at the right.
        spin.setRotate(-90f + turn * 360f, bounds.exactCenterX(), bounds.exactCenterY())
        shader.setLocalMatrix(spin)
        paint.shader = shader
        paint.alpha = if (stopping) 128 else 255
        canvas.drawRoundRect(ring, RADIUS_DP * density, RADIUS_DP * density, paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        // The stroke's middle: 12dp, between the ring's 13dp outside and 11dp inside.
        private const val RADIUS_DP = 12f

        // tokens.css --rainbow-conic: the rainbow closed into a loop.
        private val RAINBOW = ComposerBackground.COLORS + ComposerBackground.COLORS.first()

        // CSS's grayscale(1): each color's luminance, by the filter's own weights.
        private val GREYS = RAINBOW.map { color ->
            val grey = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)).toInt()
            Color.rgb(grey, grey, grey)
        }.toIntArray()
    }
}
