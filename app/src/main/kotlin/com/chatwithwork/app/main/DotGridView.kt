package com.chatwithwork.app.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/**
 * Live Wire's dot grid, fading toward the bottom, as behind the web app's new
 * chat page. Decoration only.
 */
class DotGridView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val spacing = 22f * density
    private val radius = 1f * density
    private val color = ContextCompat.getColor(context, R.color.lw_dot)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val baseAlpha = android.graphics.Color.alpha(color)
        var y = spacing / 2
        while (y < height) {
            // Full strength at the top, 60% by the middle, gone at the bottom.
            val progress = y / height
            val fade = if (progress < 0.5f) 1f - 0.8f * progress else 1.2f * (1f - progress)
            paint.color = color
            paint.alpha = (baseAlpha * fade.coerceIn(0f, 1f)).toInt()
            var x = spacing / 2
            while (x < width) {
                canvas.drawCircle(x, y, radius, paint)
                x += spacing
            }
            y += spacing
        }
    }
}
