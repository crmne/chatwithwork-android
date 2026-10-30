package com.chatwithwork.app.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable

/** A native illustration of known state, with no percentage, future stages or private text. */
object LiveArtwork {
    fun draw(context: Context, state: LivePresentation, steps: Int, recent: List<String>): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((160 * density).toInt(), (90 * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(density, density) }
        val ink = ContextCompat.getColor(context, state.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(alpha: Int) {
            paint.color = ColorUtils.setAlphaComponent(ink, alpha)
        }
        fun symbol(name: String, x: Int, y: Int, size: Int, alpha: Int = 255) {
            canvas.save()
            canvas.scale(1 / density, 1 / density)
            SymbolDrawable(context, name, sizeDp = size.toFloat(), color = ColorUtils.setAlphaComponent(ink, alpha))
                .apply {
                    setBounds(
                        (x * density).toInt(),
                        (y * density).toInt(),
                        ((x + size) * density).toInt(),
                        (
                            (y + size) *
                                density
                            ).toInt()
                    )
                    draw(canvas)
                }
            canvas.restore()
        }

        fill(24)
        canvas.drawRoundRect(0f, 0f, 160f, 90f, 16f, 16f, paint)
        when {
            state.isWorking && steps > 0 -> {
                fill(255)
                paint.typeface = ResourcesCompat.getFont(context, R.font.geist_semibold) ?: Typeface.DEFAULT_BOLD
                val number = steps.toString()
                paint.textSize = 59f
                if (paint.measureText(number) > 88f) paint.textSize *= 88f / paint.measureText(number)
                canvas.drawText(number, 14f, 66f, paint)
                // These are actual recent kinds, not a route or completion gauge.
                val trail = recent.takeLast(3).ifEmpty { listOfNotNull(state.step) }
                trail.forEachIndexed { index, kind ->
                    val current = index == trail.lastIndex && kind == state.step
                    val y = 45 - trail.size * 12 + index * 24
                    symbol(LivePresentation("answering", kind).symbol, 119, y, 24, if (current) 255 else 115)
                }
            }

            state.isWorking -> {
                fill(28)
                canvas.drawCircle(80f, 45f, 34f, paint)
                symbol(state.symbol, 55, 20, 50)
            }

            state.phase == "answered" -> {
                fill(50)
                canvas.drawRoundRect(29f, 13f, 94f, 78f, 7f, 7f, paint)
                fill(130)
                for (y in listOf(29f, 40f, 51f)) canvas.drawRoundRect(40f, y, 79f, y + 4, 2f, 2f, paint)
                fill(255)
                canvas.drawCircle(112f, 56f, 23f, paint)
                paint.color = ContextCompat.getColor(context, R.color.lw_surface)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(101f, 56f, 109f, 64f, paint)
                canvas.drawLine(109f, 64f, 124f, 48f, paint)
                paint.style = Paint.Style.FILL
            }

            state.phase == "approval_waiting" || state.phase == "input_requested" -> {
                fill(50)
                canvas.drawRoundRect(88f, 18f, 145f, 69f, 10f, 10f, paint)
                symbol(if (state.phase == "approval_waiting") "front_hand" else "question_mark", 19, 18, 55)
                symbol("chat_bubble", 97, 29, 35, 165)
            }

            else -> {
                fill(45)
                canvas.drawCircle(80f, 45f, 35f, paint)
                symbol(state.symbol, 52, 17, 56)
            }
        }
        return bitmap
    }
}
