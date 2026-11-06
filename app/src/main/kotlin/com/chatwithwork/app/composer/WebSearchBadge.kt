package com.chatwithwork.app.composer

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import com.chatwithwork.app.R

/**
 * The small globe "+" carries while web search is on and its switch is in
 * "+" (the web's `.chat-prompt__attach-badge`, components/composer.css): a
 * 14dp disc of the surface ringed by a 1dp strong line, with Phosphor's
 * filled globe at 10dp in ink, its corner 1dp past the 44dp target's top
 * and end (measured in a phone browser). It's "+"'s foreground, drawn past the button's bounds, so the
 * bar and the box don't clip their children (view_composer.xml).
 */
class WebSearchBadge(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lw_surface)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            ContextCompat.getColor(context, R.color.lw_line_strong)
    }
    private val globe = ContextCompat.getDrawable(context, R.drawable.ic_globe_fill)!!.mutate().apply {
        setTint(ContextCompat.getColor(context, R.color.lw_ink))
    }

    /** Right-to-left layouts put "+" on the right, and the badge on its left corner. */
    var rtl = false

    override fun draw(canvas: Canvas) {
        val radius = DISC_DP / 2 * density
        val past = PAST_DP * density
        val x = if (rtl) bounds.left - past + radius else bounds.right + past - radius
        val y = bounds.top - past + radius
        // box-shadow: 0 0 0 1px, a ring outside the disc.
        canvas.drawCircle(x, y, radius + density, line)
        canvas.drawCircle(x, y, radius, disc)
        val side = (GLOBE_DP * density).toInt()
        globe.setBounds(0, 0, side, side)
        canvas.withTranslation(x - side / 2f, y - side / 2f) { globe.draw(this) }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val DISC_DP = 14f
        const val GLOBE_DP = 10f
        const val PAST_DP = 1f
    }
}
