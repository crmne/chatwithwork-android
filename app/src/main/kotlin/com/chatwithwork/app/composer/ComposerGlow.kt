package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/**
 * What the composer's box throws around itself: the web's `.composer` glow,
 * which is the edge's own layer blurred (the box itself, its 20 radius, the
 * same gradient at the same place, blur 22px), and `--shadow-float` over it.
 *
 * A blur is the one expensive thing here, so it happens once for a width, in
 * software on a small alpha-only bitmap (a pixel to the dp: it's all soft),
 * which works the same on every Android version the app runs on, where
 * RenderEffect would start at 12 and redo the blur on each frame it draws.
 * The colour isn't in the bitmap: it's drawn with the edge's own shader
 * ([ComposerBackground.edgeShader]), so under any point of the edge the glow
 * is that point's colour, and while the edge flows the glow moves with it at
 * the cost of a texture, never a new blur. The bitmap's middle row stretches
 * for a taller box, so new lines, keystrokes and the keyboard only draw a
 * texture. The box covers that row where the page would see it.
 */
class ComposerGlow(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val shadowColor = ContextCompat.getColor(context, R.color.lw_shadow)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var drawnWidth = 0
    private var glow: Bitmap? = null
    private var shadow: Bitmap? = null

    /**
     * Draws around [box], in the same coordinates, before the box itself: the glow in
     * [edgeShader], the edge's rainbow (or [attention] while it has none), then the shadow.
     */
    fun draw(canvas: Canvas, box: Rect, edgeShader: Shader?, attention: Int, glowOpacity: Float) {
        val width = (box.width() / density).toInt()
        if (width <= 0) return
        if (width != drawnWidth) {
            drawnWidth = width
            glow = null
            shadow = null
        }
        val glow = glow ?: blurred(width, GLOW_HEIGHT_DP, GLOW_MARGIN, 0f, GLOW_RADIUS_DP, GLOW_SIGMA_DP)
            .also { glow = it }
        val shadow = shadow ?: blurred(width, SHADOW_HEIGHT_DP, SHADOW_MARGIN, -SHADOW_SPREAD_DP, 0f, SHADOW_SIGMA_DP)
            .also { shadow = it }

        // The edge's shader is laid out in the box's own coordinates.
        canvas.save()
        canvas.translate(box.left.toFloat(), box.top.toFloat())
        paint.color = if (edgeShader == null) attention else Color.BLACK
        paint.shader = edgeShader
        paint.alpha = (glowOpacity * 255).toInt()
        draw(canvas, glow, box.width(), box.height(), GLOW_MARGIN, 0f)
        paint.shader = null
        paint.color = shadowColor
        draw(canvas, shadow, box.width(), box.height(), SHADOW_MARGIN, SHADOW_OFFSET_DP)
        canvas.restore()
    }

    /** An alpha-only bitmap of a blurred round rect: drawn, it takes the paint's colour or shader. */
    private fun blurred(width: Int, height: Int, margin: Float, outset: Float, radius: Float, sigma: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (width + margin * 2).toInt(),
            (height + margin * 2).toInt(),
            Bitmap.Config.ALPHA_8
        )
        val inset = margin - outset
        val body = RectF(inset, inset, bitmap.width - inset, bitmap.height - inset)
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(radiusFor(sigma), BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).drawRoundRect(body, radius, radius, blur)
        bitmap.prepareToDraw()
        return bitmap
    }

    /** The bitmap's top, its middle row stretched over the box's extra height, and its bottom. */
    private fun draw(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int, margin: Float, offset: Float) {
        val outside = margin * density
        val left = -outside
        val right = width + outside
        val top = -outside + offset * density
        val bottom = height + outside + offset * density
        val middle = bitmap.height / 2
        val cap = middle * density

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

        // The web's .composer: --rainbow-glow-inset 0, its radius the box's, blur(22px), whose
        // length is the Gaussian's sigma. Three sigmas reach every visible pixel.
        const val GLOW_RADIUS_DP = ComposerBackground.RADIUS_DP
        const val GLOW_SIGMA_DP = 22f
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
