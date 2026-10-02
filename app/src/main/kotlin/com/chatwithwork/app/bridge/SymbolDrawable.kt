package com.chatwithwork.app.bridge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import com.masilotti.bridgecomponents.R as BridgeworkR

/**
 * Draws one Material Symbols icon by name ("push_pin", "share"), so the server
 * can pick any symbol for a toolbar button with `data-bridge-android-image`.
 *
 * The font is the variable Material Symbols Outlined that Joe Masilotti's
 * bridge components already ship, so it adds nothing to the app's size. A
 * selected button draws the filled variant, the way Material 3 marks state.
 */
class SymbolDrawable(
    context: Context,
    private val name: String,
    filled: Boolean = false,
    sizeDp: Float = 24f,
    @ColorInt color: Int = android.graphics.Color.BLACK
) : Drawable() {
    private val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        sizeDp,
        context.resources.displayMetrics
    ).toInt()

    private var tint: ColorStateList? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = symbolsTypeface(context)
        textSize = sizePx.toFloat()
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "'liga'"
        fontVariationSettings = "'FILL' ${if (filled) 1 else 0}, 'wght' 400, 'GRAD' 0, 'opsz' $sizeDp"
        this.color = color
    }

    override fun draw(canvas: Canvas) {
        val metrics = paint.fontMetrics
        val baseline = bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(name, bounds.exactCenterX(), baseline, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        applyTint()
    }

    override fun isStateful(): Boolean = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean = applyTint()

    private fun applyTint(): Boolean {
        val tint = tint ?: return false
        val color = tint.getColorForState(state, tint.defaultColor)
        if (color == paint.color) return false
        paint.color = color
        invalidateSelf()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    companion object {
        @Volatile private var typeface: Typeface? = null

        private fun symbolsTypeface(context: Context): Typeface? = typeface ?: synchronized(this) {
            typeface ?: ResourcesCompat.getFont(context, BridgeworkR.font.material_symbols)
                .also { typeface = it }
        }

        /**
         * Symbol names are lowercase words joined by underscores. Anything else
         * would draw as text, so callers fall back to a text button.
         */
        fun isValidName(name: String?): Boolean = parse(name) != null

        /**
         * Reads "push_pin" or "push_pin.fill": a `.fill` suffix asks for the
         * filled symbol, like SF Symbols' names on iOS ("pin.fill").
         */
        fun parse(name: String?): Symbol? {
            name ?: return null
            val filled = name.endsWith(FILL_SUFFIX)
            val base = name.removeSuffix(FILL_SUFFIX)
            return if (SYMBOL_NAME.matches(base)) Symbol(base, filled) else null
        }

        fun from(context: Context, name: String?, filledByDefault: Boolean = false): SymbolDrawable? =
            parse(name)?.let { SymbolDrawable(context, it.name, filled = it.filled || filledByDefault) }

        private const val FILL_SUFFIX = ".fill"
        private val SYMBOL_NAME = Regex("^[a-z0-9_]{1,64}$")
    }
}

/** A symbol name and whether to draw it filled. */
data class Symbol(val name: String, val filled: Boolean)
