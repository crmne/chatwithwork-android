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
    private const val BASE = 76f
    private const val BOT = 34
    private const val TRAIL_END = WIDTH * 0.56f
    private const val LOGO = 16
    private const val MAX_TICKS = 48
    private val TICK_HEIGHT =
        mapOf('x' to 27f, 'r' to 22f, 'u' to 17f, 's' to 14f, 'a' to 12f, 'l' to 10f, 'p' to 8f, 'w' to 7f)

    fun draw(context: Context, shown: LivePresentation, road: LiveRoad): Bitmap {
        val density = context.resources.displayMetrics.density
        val bitmap = createBitmap((WIDTH * density).toInt(), (HEIGHT * density).toInt())
        val canvas = Canvas(bitmap).apply { scale(density, density) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ColorUtils.setAlphaComponent(Color.GRAY, 30)
        canvas.drawRoundRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), 16f, 16f, paint)

        val ticks = shown.trail?.lowercase()?.toList() ?: List(minOf(shown.walked, MAX_TICKS)) { 's' }
        val rainbow = road.segments.size > 2 && road.segments[0].color != road.segments[1].color
        val pitch = if (ticks.isEmpty()) 0f else minOf((TRAIL_END - 8f) / ticks.size, 7f)
        val start = TRAIL_END - pitch * ticks.size
        ticks.forEachIndexed { index, kind ->
            paint.color =
                if (rainbow) LiveRoad.rainbow(index / maxOf(ticks.lastIndex, 1).toFloat()) else road.segments[0].color
            val x = start + pitch * index + pitch / 2
            paint.strokeWidth = (pitch * 0.58f).coerceIn(2f, 3.5f)
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, BASE, x, BASE - (TICK_HEIGHT[kind] ?: 14f), paint)
        }

        val ahead = road.segments.last().color
        var x = TRAIL_END + BOT + 6f
        val dots = ((WIDTH - x) / 9).toInt()
        for (index in 0 until dots) {
            val fade = 1 - index / dots.toFloat()
            paint.color = ColorUtils.setAlphaComponent(ahead, (255 * (0.15f + 0.85f * fade)).toInt())
            canvas.drawCircle(x, BASE - 2, 1.75f, paint)
            x += 9
        }

        shown.services.mapNotNull { LiveServices.of(it)?.logo }.takeLast(3).forEachIndexed { index, logo ->
            drawable(context, logo, canvas, 10f + index * (LOGO + 4), 12f, LOGO)
        }
        drawable(context, shown.face.tracker, canvas, TRAIL_END + 2, BASE - BOT + 3, BOT)
        if (shown.state == LivePresentation.State.FINISHED) {
            drawable(context, R.drawable.ic_mark, canvas, WIDTH - 32f, BASE - 26, 24)
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
