package com.chatwithwork.app.fragments

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable

/**
 * Decisions in the chat's top bar, as the web's pill (decisions/_pill,
 * decisions_pill.css): the list-checks icon and how many wait, on the
 * attention wash ringed in its edge, or the icon alone, in muted ink, at
 * none. It's drawn as the toolbar's logo, the one slot Material's toolbar
 * lays out between the navigation button and the title.
 */
class DecisionsPillDrawable(context: Context, private val count: Int) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val waiting = count > 0
    private val ink =
        ContextCompat.getColor(context, if (waiting) R.color.lw_attention_ink else R.color.lw_ink_muted)
    private val icon = SymbolDrawable(context, "checklist", sizeDp = ICON_DP, color = ink)
    private val label = count.toString()
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, COUNT_SP, context.resources.displayMetrics)
            typeface = ResourcesCompat.getFont(context, R.font.geist_semibold)
            fontFeatureSettings = "'tnum'"
        }
    private val wash =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.lw_attention_wash) }
    private val edge =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.lw_attention_edge)
            style = Paint.Style.STROKE
            strokeWidth = density
        }

    private fun dp(value: Float) = value * density

    override fun getIntrinsicHeight(): Int = Math.round(dp(HEIGHT_DP))

    override fun getIntrinsicWidth(): Int = Math.round(
        if (waiting) {
            dp(PAD_START_DP + ICON_DP + GAP_DP + PAD_END_DP) + text.measureText(label)
        } else {
            dp(HEIGHT_DP)
        }
    )

    override fun draw(canvas: Canvas) {
        val box = RectF(bounds)
        if (waiting) {
            val radius = box.height() / 2f
            canvas.drawRoundRect(box, radius, radius, wash)
            val inset = edge.strokeWidth / 2f
            box.inset(inset, inset)
            canvas.drawRoundRect(box, radius - inset, radius - inset, edge)
        }
        val iconSize = dp(ICON_DP).toInt()
        val iconLeft =
            if (waiting) bounds.left + dp(PAD_START_DP).toInt() else bounds.left + (bounds.width() - iconSize) / 2
        val iconTop = bounds.top + (bounds.height() - iconSize) / 2
        icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        icon.draw(canvas)
        if (waiting) {
            val metrics = text.fontMetrics
            val baseline = bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, iconLeft + iconSize + dp(GAP_DP), baseline, text)
        }
    }

    override fun setAlpha(alpha: Int) {
        text.alpha = alpha
        wash.alpha = alpha
        edge.alpha = alpha
        icon.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val HEIGHT_DP = 28f
        const val ICON_DP = 18f
        const val GAP_DP = 5f
        const val PAD_START_DP = 8f
        const val PAD_END_DP = 10f
        const val COUNT_SP = 12f
    }
}
