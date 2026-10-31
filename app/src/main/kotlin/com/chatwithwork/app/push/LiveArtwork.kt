package com.chatwithwork.app.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.chatwithwork.app.R

/**
 * The road below Android 16, which has no ProgressStyle: a picture for the
 * large icon, with the same parts as [LiveRoad]. Ticks are the steps seen,
 * the bot stands where they end, and the dots ahead fade out, never reaching
 * an end until there is one. It draws no text, count or percentage.
 */
object LiveArtwork {
    private const val WIDTH = 160
    private const val HEIGHT = 90
    private const val BASE = 80f
    private const val BOT = 40
    private const val TRAIL_END = WIDTH * 0.5f
    private const val LOGO = 26
    private const val MAX_TICKS = 48
    private val TICK_HEIGHT =
        mapOf('x' to 40f, 'r' to 33f, 'u' to 26f, 's' to 21f, 'a' to 18f, 'l' to 15f, 'p' to 12f, 'w' to 10f)

    fun draw(context: Context, shown: LivePresentation, road: LiveRoad): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = createBitmap((WIDTH * density).toInt(), (HEIGHT * density).toInt())
        val canvas = Canvas(bitmap).apply { scale(density, density) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ColorUtils.setAlphaComponent(Color.GRAY, 30)
        canvas.drawRoundRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), 16f, 16f, paint)

        val ticks = shown.trail?.lowercase()?.toList() ?: List(minOf(shown.walked, MAX_TICKS)) { 's' }
        val rainbow = road.segments.size > 2 && road.segments[0].color != road.segments[1].color
        val pitch = if (ticks.isEmpty()) 0f else minOf((TRAIL_END - 8f) / ticks.size, 11f)
        val start = TRAIL_END - pitch * ticks.size
        ticks.forEachIndexed { index, kind ->
            paint.color =
                if (rainbow) LiveRoad.rainbow(index / maxOf(ticks.lastIndex, 1).toFloat()) else road.segments[0].color
            val x = start + pitch * index + pitch / 2
            paint.strokeWidth = (pitch * 0.58f).coerceIn(2f, 6f)
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, BASE, x, BASE - (TICK_HEIGHT[kind] ?: 21f), paint)
        }

        val ahead = road.segments.last().color
        var x = TRAIL_END + BOT + 8f
        val dots = ((WIDTH - x) / 12).toInt() + 1
        for (index in 0 until dots) {
            val fade = 1 - index / dots.toFloat()
            paint.color = ColorUtils.setAlphaComponent(ahead, (255 * (0.15f + 0.85f * fade)).toInt())
            canvas.drawCircle(x, BASE - 3, 3f, paint)
            x += 12
        }

        shown.services.mapNotNull { LiveServices.of(it)?.logo }.takeLast(3).forEachIndexed { index, logo ->
            drawable(context, logo, canvas, 8f + index * (LOGO + 4), 8f, LOGO)
        }
        drawable(context, shown.face.tracker, canvas, TRAIL_END + 2, BASE - BOT + 5, BOT)
        if (shown.state == LivePresentation.State.FINISHED) {
            drawable(context, R.drawable.ic_mark, canvas, WIDTH - 40f, BASE - 34, 32)
        }
        return bitmap
    }

    private fun drawable(context: Context, id: Int, canvas: Canvas, x: Float, y: Float, size: Int) {
        val drawable = ContextCompat.getDrawable(context, id) ?: return
        canvas.withTranslation(x, y) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(this)
        }
    }
}
