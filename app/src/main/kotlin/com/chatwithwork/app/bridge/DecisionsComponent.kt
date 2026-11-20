package com.chatwithwork.app.bridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.chatwithwork.app.R
import com.chatwithwork.app.fragments.WebFragment
import com.chatwithwork.app.fragments.WebScreenHost
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `decisions`: the top app bar's last button is Decisions on every page but
 * Decisions, where it stays New chat (docs/server-contract.md, "Decisions in
 * the bar"). The page says how many wait for the person here, live:
 * `connect {waiting, here, url}`, then `count {waiting}` whenever it changes.
 * While some wait the button takes the attention ink with a count badge.
 * Builds without the component, and servers that don't render it, keep New
 * chat there.
 */
class DecisionsComponent(name: String, bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    ToolbarComponent(name, bridgeDelegate) {
    override val menuGroup = GROUP_DECISIONS

    private var data: Data? = null

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> data = message.data<Data>() ?: return

            "count" -> {
                val waiting = message.data<Count>()?.waiting ?: return
                data = data?.copy(waiting = waiting) ?: return
            }

            else -> return
        }
        redraw()
    }

    override fun render(toolbar: Toolbar) {
        val data = data ?: return
        val screen = destination.fragment as? WebFragment
        // On Decisions the bar's New chat stays; everywhere else Decisions takes its place.
        screen?.showDecisionsInBar(!data.here)
        if (data.here) return
        val context = toolbar.context
        val waiting = data.waiting.coerceAtLeast(0)
        val title =
            if (waiting > 0) {
                context.resources.getQuantityString(R.plurals.bar_decisions_waiting, waiting, waiting)
            } else {
                context.getString(R.string.bar_decisions)
            }
        toolbar.menu.add(menuGroup, ITEM_DECISIONS, ORDER_DECISIONS, R.string.bar_decisions).apply {
            icon = DecisionsIcon(context, waiting)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            describe(title)
            setOnMenuItemClickListener {
                (destination.fragment.activity as? WebScreenHost)?.openDecisionsFromBar(data.url)
                true
            }
        }
    }

    @Serializable
    data class Data(val waiting: Int = 0, val here: Boolean = false, val url: String? = null)

    @Serializable
    data class Count(val waiting: Int = 0)

    companion object {
        const val NAME = "decisions"

        val factory = BridgeComponentFactory(NAME, ::DecisionsComponent)

        private const val ITEM_DECISIONS = 0x0C701

        /** How the badge reads: the count up to 9, then "9+"; nothing at zero. */
        fun badge(waiting: Int): String? = when {
            waiting <= 0 -> null
            waiting > 9 -> "9+"
            else -> waiting.toString()
        }
    }
}

/**
 * Decisions' signpost in the bar's ink, or in the attention ink with a count
 * at its top right, on the attention wash ringed in its edge, as the
 * sidebar's Decisions count (sidebar_decisions_count).
 */
private class DecisionsIcon(context: Context, waiting: Int) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val label = DecisionsComponent.badge(waiting)
    private val glyph: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_bar_decisions)!!.mutate().apply {
            if (label != null) setTint(ContextCompat.getColor(context, R.color.lw_attention_ink))
        }
    private val wash = paint(ContextCompat.getColor(context, R.color.lw_attention_wash))
    private val edge =
        paint(ContextCompat.getColor(context, R.color.lw_attention_edge)).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    private val text =
        paint(ContextCompat.getColor(context, R.color.lw_attention_ink)).apply {
            // Grows with larger text, up to a third more, so it stays a badge.
            textSize = BADGE_TEXT_SP * density * context.resources.configuration.fontScale.coerceIn(1f, 1.3f)
            typeface = ResourcesCompat.getFont(context, R.font.geist_semibold)
            textAlign = Paint.Align.CENTER
        }

    override fun getIntrinsicWidth() = glyph.intrinsicWidth

    override fun getIntrinsicHeight() = glyph.intrinsicHeight

    override fun draw(canvas: Canvas) {
        glyph.bounds = bounds
        glyph.draw(canvas)
        val label = label ?: return
        val height = BADGE_HEIGHT_DP * density
        val width = maxOf(height, text.measureText(label) + BADGE_PADDING_DP * 2 * density)
        // Over the glyph's top right corner, like a dock's badge.
        val right = bounds.right + width / 2
        val top = bounds.top - height / 2
        val pill = RectF(right - width, top, right, top + height)
        canvas.drawRoundRect(pill, height / 2, height / 2, wash)
        canvas.drawRoundRect(pill, height / 2, height / 2, edge)
        val baseline = pill.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, pill.centerX(), baseline, text)
    }

    override fun setAlpha(alpha: Int) {
        glyph.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glyph.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private companion object {
        const val BADGE_HEIGHT_DP = 15f
        const val BADGE_PADDING_DP = 4f
        const val BADGE_TEXT_SP = 10f
    }
}
