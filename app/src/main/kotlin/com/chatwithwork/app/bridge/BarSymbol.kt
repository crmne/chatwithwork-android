package com.chatwithwork.app.bridge

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/**
 * A symbol the server names for the top app bar, drawn like the bar's own
 * icons (`ic_menu`, `ic_more`, `ic_new_chat`): Material Symbols Rounded, weight
 * 300, optical size 48, at 22dp.
 *
 * Only Outlined ships as a font, so the symbols the server puts on the bar are
 * vectors. Add a new one here, from Google's `wght300/48px` download. A name
 * without a vector still draws, from the font at the same size and thickness,
 * with Outlined's square corners.
 */
object BarSymbol {
    private const val SIZE_DP = 22f
    private const val OPTICAL_SIZE = 48f

    private val VECTORS = mapOf(
        Symbol("add", filled = false) to R.drawable.ic_add,
        Symbol("edit_square", filled = false) to R.drawable.ic_new_chat,
        Symbol("share", filled = false) to R.drawable.ic_share,
        Symbol("right_panel_open", filled = false) to R.drawable.ic_right_panel_open,
        Symbol("counter_1", filled = false) to R.drawable.ic_counter_1,
        Symbol("counter_1", filled = true) to R.drawable.ic_counter_1_fill,
        Symbol("counter_2", filled = false) to R.drawable.ic_counter_2,
        Symbol("counter_2", filled = true) to R.drawable.ic_counter_2_fill,
        Symbol("counter_3", filled = false) to R.drawable.ic_counter_3,
        Symbol("counter_3", filled = true) to R.drawable.ic_counter_3_fill,
        Symbol("counter_4", filled = false) to R.drawable.ic_counter_4,
        Symbol("counter_4", filled = true) to R.drawable.ic_counter_4_fill,
        Symbol("counter_5", filled = false) to R.drawable.ic_counter_5,
        Symbol("counter_5", filled = true) to R.drawable.ic_counter_5_fill,
        Symbol("counter_6", filled = false) to R.drawable.ic_counter_6,
        Symbol("counter_6", filled = true) to R.drawable.ic_counter_6_fill,
        Symbol("counter_7", filled = false) to R.drawable.ic_counter_7,
        Symbol("counter_7", filled = true) to R.drawable.ic_counter_7_fill,
        Symbol("counter_8", filled = false) to R.drawable.ic_counter_8,
        Symbol("counter_8", filled = true) to R.drawable.ic_counter_8_fill,
        Symbol("counter_9", filled = false) to R.drawable.ic_counter_9,
        Symbol("counter_9", filled = true) to R.drawable.ic_counter_9_fill,
        Symbol("filter_9_plus", filled = false) to R.drawable.ic_filter_9_plus,
        Symbol("filter_9_plus", filled = true) to R.drawable.ic_filter_9_plus_fill
    )

    /** The vector for a server's name ("counter_3.fill"), if the bar has one. */
    @DrawableRes
    fun vector(name: String?): Int? = SymbolDrawable.parse(name)?.let(VECTORS::get)

    fun from(context: Context, name: String?): Drawable? {
        val symbol = SymbolDrawable.parse(name) ?: return null
        val vector = VECTORS[symbol] ?: return SymbolDrawable(
            context,
            symbol.name,
            filled = symbol.filled,
            sizeDp = SIZE_DP,
            opticalSize = OPTICAL_SIZE
        )
        return ContextCompat.getDrawable(context, vector)
    }
}
