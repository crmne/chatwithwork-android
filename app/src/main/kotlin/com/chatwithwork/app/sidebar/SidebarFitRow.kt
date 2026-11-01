package com.chatwithwork.app.sidebar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/**
 * A row whose name sits at its own width with what follows it beside it, the
 * rest of the row in its [Space]. A plain LinearLayout measures the name
 * first, so a long one would push out what follows; here it takes only what's
 * left and ellipsizes.
 */
class SidebarFitRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private val children get() = (0 until childCount).map(::getChildAt)
    private val name get() = children.first { it is TextView }
    private val space get() = children.first { it is Space }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fit(LayoutParams.WRAP_CONTENT, nameWeight = 0f, spaceWeight = 1f)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        val used = children.filter { it.visibility != View.GONE && it !is Space }.sumOf { child ->
            val params = child.layoutParams as LayoutParams
            child.measuredWidth + params.marginStart + params.marginEnd
        }
        if (used > available) {
            fit(0, nameWeight = 1f, spaceWeight = 0f)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun fit(width: Int, nameWeight: Float, spaceWeight: Float) {
        (name.layoutParams as LayoutParams).apply {
            this.width = width
            weight = nameWeight
        }
        (space.layoutParams as LayoutParams).weight = spaceWeight
    }
}
