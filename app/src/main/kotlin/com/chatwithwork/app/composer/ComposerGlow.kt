package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/**
 * What the composer's box throws around itself: the web's `.rainbow-glow`
 * (0.4rem past the box, a 28 radius, blur 20px) and `--shadow-float`.
 *
 * A blur is the one expensive thing here, so it happens once for a width, in
 * software on a small bitmap (a pixel to the dp: it's all soft), which works
 * the same on every Android version the app runs on, where RenderEffect would
 * start at 12 and redo the blur on each frame it draws. The bitmap's middle
 * row stretches for a taller box, so new lines, keystrokes and the keyboard
 * only draw a texture. The box covers that row where the page would see it.
 */
class ComposerGlow(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val attention = ContextCompat.getColor(context, R.color.lw_attention)
    private val shadowColor = ContextCompat.getColor(context, R.color.lw_shadow)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var drawnWidth = 0
    private var rainbow: Bitmap? = null
    private var amber: Bitmap? = null
    private var shadow: Bitmap? = null

    /** Draws around [box], in the same coordinates, before the box itself. */
    fun draw(canvas: Canvas, box: Rect, look: ComposerLook, glowOpacity: Float) {
        val width = (box.width() / density).toInt()
        if (width <= 0) return
        if (width != drawnWidth) {
            drawnWidth = width
            rainbow = null
            amber = null
            shadow = null
        }
        val shadow = shadow ?: shadowBitmap(width).also { shadow = it }
        val glow = if (look.edge == ComposerLook.Edge.ATTENTION) {
            amber ?: glowBitmap(width, true).also { amber = it }
        } else {
            rainbow ?: glowBitmap(width, false).also { rainbow = it }
        }
        draw(canvas, shadow, box, SHADOW_MARGIN, SHADOW_OFFSET_DP, 1f)
        draw(canvas, glow, box, GLOW_MARGIN, 0f, glowOpacity)
    }

    private fun glowBitmap(width: Int, amber: Boolean): Bitmap =
        blurred(width, GLOW_HEIGHT_DP, GLOW_MARGIN, GLOW_OUTSET_DP, GLOW_RADIUS_DP, GLOW_SIGMA_DP) { body ->
            if (amber) {
                color = attention
            } else {
                shader = LinearGradient(
                    body.left - body.width() * 0.0055f,
                    0f,
                    body.left + body.width() * 0.9934f,
                    0f,
                    ComposerBackground.COLORS,
                    ComposerBackground.POSITIONS,
                    Shader.TileMode.CLAMP
                )
            }
        }

    private fun shadowBitmap(width: Int): Bitmap =
        blurred(width, SHADOW_HEIGHT_DP, SHADOW_MARGIN, -SHADOW_SPREAD_DP, 0f, SHADOW_SIGMA_DP) { color = shadowColor }

    private fun blurred(
        width: Int,
        height: Int,
        margin: Float,
        outset: Float,
        radius: Float,
        sigma: Float,
        fill: Paint.(RectF) -> Unit
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (width + margin * 2).toInt(),
            (height + margin * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val inset = margin - outset
        val body = RectF(inset, inset, bitmap.width - inset, bitmap.height - inset)
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(radiusFor(sigma), BlurMaskFilter.Blur.NORMAL)
            fill(body)
        }
        Canvas(bitmap).drawRoundRect(body, radius, radius, blur)
        bitmap.prepareToDraw()
        return bitmap
    }

    /** The bitmap's top, its middle row stretched over the box's extra height, and its bottom. */
    private fun draw(canvas: Canvas, bitmap: Bitmap, box: Rect, margin: Float, offset: Float, opacity: Float) {
        val outside = margin * density
        val left = box.left - outside
        val right = box.right + outside
        val top = box.top - outside + offset * density
        val bottom = box.bottom + outside + offset * density
        val middle = bitmap.height / 2
        val cap = middle * density

        paint.alpha = (opacity * 255).toInt()
        source.set(0, 0, bitmap.width, middle)
        target.set(left, top, right, top + cap)
        canvas.drawBitmap(bitmap, source, target, paint)
        source.set(0, middle - 1, bitmap.width, middle + 1)
        target.set(left, top + cap, right, bottom - cap)
        canvas.drawBitmap(bitmap, source, target, paint)
        source.set(0, middle, bitmap.width, bitmap.height)
        target.set(left, bottom - cap, right, bottom)
        canvas.drawBitmap(bitmap, source, target, paint)
    }

    companion object {
        // The box heights the bitmaps are drawn for: no composer is shorter, and a taller
        // one stretches the row the box hides. The shadow's is a shorter shape, so nearer the real one.
        private const val GLOW_HEIGHT_DP = 80
        private const val SHADOW_HEIGHT_DP = 100

        private const val GLOW_OUTSET_DP = 6.4f
        private const val GLOW_RADIUS_DP = 28f
        private const val GLOW_SIGMA_DP = 20f
        private const val GLOW_MARGIN = 67f

        // --shadow-float: 0 20px 50px -24px, whose blur is a sigma of half that.
        private const val SHADOW_OFFSET_DP = 20f
        private const val SHADOW_SPREAD_DP = 24f
        private const val SHADOW_SIGMA_DP = 25f
        private const val SHADOW_MARGIN = 52f

        // Skia's own conversion, undone: BlurMaskFilter takes a radius.
        private const val SIGMA_PER_RADIUS = 0.57735f
        private const val SIGMA_BASE = 0.5f

        fun radiusFor(sigma: Float): Float = (sigma - SIGMA_BASE) / SIGMA_PER_RADIUS
    }
}
