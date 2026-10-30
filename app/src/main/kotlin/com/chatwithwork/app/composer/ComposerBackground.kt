package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/** Web tokens.css --rainbow, including all five stops, over --surface. */
class ComposerBackground(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            ContextCompat.getColor(context, R.color.lw_surface)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    var edgeOpacity = 0.7f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        edge.shader = LinearGradient(
            bounds.left - bounds.width() * 0.0055f,
            0f,
            bounds.left + bounds.width() * 0.9934f,
            0f,
            COLORS,
            POSITIONS,
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val box = RectF(bounds)
        canvas.drawRoundRect(box, 20 * density, 20 * density, surface)
        box.inset(density, density)
        edge.alpha = (edgeOpacity * 255).toInt()
        canvas.drawRoundRect(box, 19 * density, 19 * density, edge)
    }

    override fun setAlpha(alpha: Int) {
        surface.alpha = alpha
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        surface.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        val COLORS =
            intArrayOf(
                0xFF44FF9A.toInt(),
                0xFF44B0FF.toInt(),
                0xFF8B44FF.toInt(),
                0xFFFF6644.toInt(),
                0xFFEBFF70.toInt()
            )
        val POSITIONS = floatArrayOf(0f, 0.2341f / 0.9989f, 0.4891f / 0.9989f, 0.7388f / 0.9989f, 1f)
    }
}
