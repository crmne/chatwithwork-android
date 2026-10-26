package com.chatwithwork.app.main

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * A LinearLayout no wider than its android:maxWidth, however wide its parent
 * lets it be: a view's own maxWidth does nothing for match_parent, so the
 * welcome screen's buttons spanned a tablet's whole width.
 */
class MaxWidthLinearLayout(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val maxWidth: Int

    init {
        val values = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxWidth))
        maxWidth = values.getDimensionPixelSize(0, Int.MAX_VALUE)
        values.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val spec =
            if (width > maxWidth) {
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY)
            } else {
                widthMeasureSpec
            }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
