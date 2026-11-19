package com.chatwithwork.app.composer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chatwithwork.app.R
import kotlin.math.ceil

/**
 * The "+" sheet's tiles (Camera, Photos, Files, Scan, Dictate): one row of
 * equal tiles, as they always are, while every label fits its tile. With the
 * system's text large enough that a label would be cut ("Ca" for Camera), the
 * tiles wrap onto as many rows as it takes for every label to read whole, the
 * rows equally wide and each row as tall as its tallest tile.
 */
class AttachTiles @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs) {
    private val rowGap = (ROW_GAP_DP * resources.displayMetrics.density).toInt()

    /** How many tiles share a row now. */
    var columns = 0
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val tiles = shown()
        if (tiles.isEmpty()) {
            columns = 0
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), paddingTop + paddingBottom)
            return
        }
        columns = (tiles.size downTo 1).firstOrNull { n -> tiles.all { fits(it, tileWidth(it, width, n)) } } ?: 1
        var height = paddingTop + paddingBottom
        tiles.chunked(columns).forEachIndexed { index, row ->
            row.forEach { tile ->
                tile.measure(
                    MeasureSpec.makeMeasureSpec(tileWidth(tile, width, columns), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
            }
            val tallest = row.maxOf { it.measuredHeight }
            row.forEach { tile ->
                if (tile.measuredHeight != tallest) {
                    tile.measure(
                        MeasureSpec.makeMeasureSpec(tile.measuredWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY)
                    )
                }
            }
            height += tallest + if (index > 0) rowGap else 0
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val width = r - l
        var top = paddingTop
        shown().chunked(columns.coerceAtLeast(1)).forEach { row ->
            var start = paddingStart
            row.forEach { tile ->
                val params = tile.layoutParams as MarginLayoutParams
                start += params.marginStart
                val left = if (rtl) width - start - tile.measuredWidth else start
                tile.layout(left, top, left + tile.measuredWidth, top + tile.measuredHeight)
                start += tile.measuredWidth + params.marginEnd
            }
            top += row.maxOf { it.measuredHeight } + rowGap
        }
    }

    private fun shown() = (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }

    private fun tileWidth(tile: View, width: Int, columns: Int): Int {
        val params = tile.layoutParams as MarginLayoutParams
        return (width / columns - params.marginStart - params.marginEnd).coerceAtLeast(0)
    }

    private fun fits(tile: View, width: Int): Boolean {
        val label = tile.findViewById<TextView>(R.id.attach_tile_label) ?: return true
        val needed = ceil(label.paint.measureText(label.text.toString())).toInt() +
            label.paddingStart + label.paddingEnd + tile.paddingStart + tile.paddingEnd
        return needed <= width
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    private companion object {
        const val ROW_GAP_DP = 8
    }
}
