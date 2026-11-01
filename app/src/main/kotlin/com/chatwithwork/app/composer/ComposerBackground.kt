package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/**
 * The box: --surface inside a 2dp edge of Web tokens.css --rainbow (all five
 * stops), --rainbow-loop while an answer runs, or --attention. The flow only
 * moves a shader's matrix, so a frame of it redraws a stroke and nothing else.
 */
class ComposerBackground(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val attention = ContextCompat.getColor(context, R.color.lw_attention)
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            ContextCompat.getColor(context, R.color.lw_surface)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    private val box = RectF()
    private val flowMatrix = Matrix()
    private var loop: LinearGradient? = null

    var look = ComposerLook()
        set(value) {
            val restyle = value.edge != field.edge
            field = value
            if (restyle) styleEdge()
            invalidateSelf()
        }

    /** How far through its loop the flowing edge is, 0 to 1. */
    var flow = 0f
        set(value) {
            field = value
            flowMatrix.setTranslate(value * bounds.width() * LOOP_WIDTHS, 0f)
            loop?.setLocalMatrix(flowMatrix)
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) = styleEdge()

    private fun styleEdge() {
        loop = null
        edge.shader = when (look.edge) {
            ComposerLook.Edge.RAINBOW -> LinearGradient(
                bounds.left - bounds.width() * 0.0055f,
                0f,
                bounds.left + bounds.width() * 0.9934f,
                0f,
                COLORS,
                POSITIONS,
                Shader.TileMode.CLAMP
            )

            // background-size 200%: a loop is two box widths, and it moves right by one.
            ComposerLook.Edge.FLOWING -> LinearGradient(
                0f,
                0f,
                (bounds.width().coerceAtLeast(1) * LOOP_WIDTHS).toFloat(),
                0f,
                LOOP_COLORS,
                null,
                Shader.TileMode.REPEAT
            ).also { gradient ->
                flowMatrix.setTranslate(flow * bounds.width() * LOOP_WIDTHS, 0f)
                gradient.setLocalMatrix(flowMatrix)
                loop = gradient
            }

            ComposerLook.Edge.ATTENTION -> {
                edge.color = attention
                null
            }
        }
    }

    override fun draw(canvas: Canvas) {
        box.set(bounds)
        canvas.drawRoundRect(box, 20 * density, 20 * density, surface)
        box.inset(density, density)
        edge.alpha = (look.edgeOpacity * 255).toInt()
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
        private val LOOP_COLORS = COLORS + COLORS.first()
        private const val LOOP_WIDTHS = 2
    }
}
