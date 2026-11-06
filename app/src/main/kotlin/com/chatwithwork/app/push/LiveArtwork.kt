package com.chatwithwork.app.push

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import com.chatwithwork.app.R
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The road on a Live Update's card, drawn as a picture because a notification
 * can show nothing else this rich: what the answer did so far, the bot where it
 * stands, and dots that fade to nothing ahead of it. The bot's place never
 * follows progress, and the road has no end mark until the answer has one.
 * A port of the iOS app's `RoadView.swift`, in the same points, on the same
 * always dark ground.
 *
 * The card's words are pictures too, in Geist, which views in a notification
 * can't load. It all goes to the system as PNG data, a few kilobytes where the
 * same picture as a bitmap would be half a megabyte in every update's binder call.
 */
object LiveArtwork {
    private const val BOT = 42f + 2f / 3f
    private const val SMALL_BOT = 18f
    private const val STOP_SIZE = 23f
    private const val STOP_LOGO = 15f
    private const val DOT_GAP = 9f
    private const val PNG_QUALITY = 100
    private const val LOGO_GAP = 6f
    private val RAINBOW =
        intArrayOf(0xFF44FF9A.toInt(), 0xFF44B0FF.toInt(), 0xFF8B44FF.toInt(), 0xFFFF6644.toInt(), 0xFFEBFF70.toInt())

    /** The road, [width] by [height] in dp. */
    fun road(context: Context, shown: LivePresentation, width: Float, height: Float): Icon {
        val scale = context.resources.displayMetrics.density
        val bitmap = createBitmap((width * scale).roundToInt(), (height * scale).roundToInt())
        Road(dark(context), Canvas(bitmap).apply { scale(scale, scale) }, shown, width, height, scale).draw()
        return bitmap.toIcon()
    }

    /** A style of the card's words. Views in notifications can't load the app's fonts, but a picture can. */
    data class Type(@param:FontRes val font: Int, val sizeSp: Float, val tracking: Float = 0f)

    /** Words as an image, and their height in pixels, which caps how far a view may stretch them. */
    class Picture(val icon: Icon, val height: Int)

    /** A run of the card's words, with [logo]'s service between [words] and [after]. */
    fun words(
        context: Context,
        words: String,
        type: Type,
        @ColorInt color: Int,
        logo: String? = null,
        after: String? = null,
        logoSizeSp: Float = 0f
    ): Picture {
        val metrics = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, type.sizeSp, metrics)
            typeface = ResourcesCompat.getFont(context, type.font)
            letterSpacing = type.tracking
        }
        val logoSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, logoSizeSp, metrics)
        val gap = LOGO_GAP * metrics.density
        val hasLogo = logoSize > 0 && LiveServices.of(logo) != null
        val fit = paint.fontMetrics
        val height = ceil(max(fit.descent - fit.ascent, logoSize)).toInt()
        val first = paint.measureText(words)
        val second = after?.let { paint.measureText(it) } ?: 0f
        val width = first + (if (hasLogo) gap + logoSize else 0f) + (if (after != null) gap + second else 0f)
        val bitmap = createBitmap(max(ceil(width).toInt(), 1), height)
        val canvas = Canvas(bitmap)
        val baseline = (height - fit.descent - fit.ascent) / 2
        canvas.drawText(words, 0f, baseline, paint)
        var x = first + gap
        if (hasLogo) {
            drawLogo(dark(context), canvas, logo, x + logoSize / 2, height / 2f, logoSize, color)
            x += logoSize + gap
        }
        after?.let { canvas.drawText(it, x, baseline, paint) }
        return Picture(bitmap.toIcon(), height)
    }

    /** The card is dark in both themes, so its logos are too. */
    private fun dark(context: Context) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or Configuration.UI_MODE_NIGHT_YES
        }
    )

    private fun Bitmap.toIcon(): Icon {
        val png = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        recycle()
        return Icon.createWithData(png.toByteArray(), 0, png.size())
    }

    private fun drawLogo(
        context: Context,
        canvas: Canvas,
        key: String?,
        centreX: Float,
        centreY: Float,
        size: Float,
        @ColorInt mono: Int
    ) {
        val service = LiveServices.of(key) ?: return
        val drawable = ContextCompat.getDrawable(context, service.logo)?.mutate() ?: return
        if (LiveServices.isMonochrome(key)) drawable.colorFilter = PorterDuffColorFilter(mono, PorterDuff.Mode.SRC_IN)
        drawable.isFilterBitmap = true
        drawable.setBounds(0, 0, size.roundToInt(), size.roundToInt())
        canvas.withTranslation(centreX - size / 2, centreY - size / 2) { drawable.draw(this) }
    }

    private class Road(
        private val context: Context,
        private val canvas: Canvas,
        private val shown: LivePresentation,
        private val w: Float,
        height: Float,
        private val scale: Float
    ) {
        private val base = height - 2
        private val ticks = shown.ticks
        private val stopKeys = shown.stops
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val canvasColor = color(R.color.road_canvas)
        private val grey = color(R.color.road_grey)
        private val line = color(R.color.road_line)
        private val positive = color(R.color.road_positive)
        private val negative = color(R.color.road_negative)
        private val ink = color(R.color.road_ink)
        private val attention = color(R.color.road_attention)

        @ColorInt
        private fun color(@ColorRes id: Int) = ContextCompat.getColor(context, id)

        fun draw() = when (shown.state) {
            LivePresentation.State.STARTING -> starting()
            LivePresentation.State.WORKING -> working()
            LivePresentation.State.AGENTS -> agents()
            LivePresentation.State.WRITING -> writing()
            LivePresentation.State.DECISION, LivePresentation.State.INPUT -> yourTurn()
            LivePresentation.State.FINISHED -> finished()
            LivePresentation.State.FAILED -> broken()
            LivePresentation.State.STOPPED -> halted()
            LivePresentation.State.QUIET -> quiet()
        }

        // Parts

        private fun span(x1: Float) = max(min(x1 - 8, ticks.size * 13f + stopKeys.size * 30f), 0f)

        private fun stopCentres(x1: Float): List<Float> {
            val width = span(x1)
            val gap = width / (stopKeys.size + 0.4f)
            return stopKeys.indices.map { x1 - width + 12 + gap * it }
        }

        /** Ticks up to [x1] that [only] keeps, in [paint], with those under a stop left out. */
        private fun drawTicks(
            x1: Float,
            paint: Paint,
            only: (Int, LivePresentation.Tick) -> Boolean = { _, _ -> true }
        ) {
            if (ticks.isEmpty()) return
            val width = span(x1)
            val pitch = width / ticks.size
            val tick = (pitch * 0.58f).coerceIn(2f, 3.5f)
            val stops = stopCentres(x1)
            ticks.forEachIndexed { index, item ->
                val x = x1 - width + index * pitch + (pitch - tick) / 2
                if (only(index, item) && stops.none { x + tick > it - 14 && x < it + 14 }) {
                    val top = base - 2 - item.height
                    canvas.drawRoundRect(x, top, x + tick, base - 2, tick / 2, tick / 2, paint)
                }
            }
        }

        private fun road(to: Float, paint: Paint, thickness: Float = 1.5f) {
            canvas.drawRect(0f, base - thickness / 2, max(to, 0f), base + thickness / 2, paint)
        }

        private fun solid(@ColorInt color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        private fun rainbow(from: Float, to: Float, fade: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val colors = RAINBOW.copyOf().apply {
                set(0, ColorUtils.setAlphaComponent(RAINBOW[0], (fade * 255).toInt()))
            }
            shader = LinearGradient(from, 0f, max(to, from + 1), 0f, colors, null, Shader.TileMode.CLAMP)
        }

        private fun origin(@ColorInt color: Int) {
            fill.style = Paint.Style.FILL
            fill.color = canvasColor
            canvas.drawCircle(3f, base, 2.5f, fill)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 1.5f
            fill.color = color
            canvas.drawCircle(3f, base, 2.5f, fill)
            fill.style = Paint.Style.FILL
        }

        private fun stops(x1: Float) = stopKeys.zip(stopCentres(x1)).forEach { (key, centre) ->
            fill.color = canvasColor
            canvas.drawCircle(centre, base - 11, STOP_SIZE / 2 + 2, fill)
            fill.color = color(R.color.road_plinth)
            canvas.drawCircle(centre, base - 11, STOP_SIZE / 2, fill)
            logo(key, centre, base - 11, STOP_LOGO, ink)
        }

        private fun logo(key: String?, centreX: Float, centreY: Float, size: Float, @ColorInt mono: Int) =
            drawLogo(context, canvas, key, centreX, centreY, size, mono)

        private fun dots(from: Float, y: Float) {
            val count = max((w - 1 - from) / DOT_GAP, 0f).toInt() + 1
            val total = max(((w - from) / DOT_GAP).toInt(), 1)
            for (index in 0 until count) {
                val opacity = 0.7 * max(1 - index.toDouble() / total, 0.0).pow(1.6) + 0.03
                fill.color = ColorUtils.setAlphaComponent(Color.WHITE, (opacity * 255).toInt())
                canvas.drawCircle(from + index * DOT_GAP, y, 1.5f, fill)
            }
        }

        private fun square(size: Float) = max((size * scale / 16).roundToInt(), 1) / scale

        private fun pixels(rects: List<RectF>, square: Float, x: Float, y: Float, @ColorInt color: Int) {
            fill.color = color
            for (pixel in rects) {
                canvas.drawRect(
                    x + pixel.left * square,
                    y + pixel.top * square,
                    x + pixel.right * square,
                    y + pixel.bottom * square,
                    fill
                )
            }
        }

        /** Chat with Work's logo where the bot would stand, in its own white and black. */
        private fun logo(x: Float, top: Float, side: Float) {
            ContextCompat.getDrawable(context, R.drawable.ic_mark)?.mutate()?.run {
                setBounds(x.roundToInt(), top.roundToInt(), (x + side).roundToInt(), (top + side).roundToInt())
                draw(canvas)
            }
        }

        /** Whole device pixels a square, so edges stay sharp. */
        private fun bot(
            x: Float,
            face: LivePresentation.Face = shown.face,
            tool: LivePresentation.Tool? = shown.tool,
            size: Float = BOT,
            bottom: Float = base
        ) {
            val square = square(size)
            val top = bottom - size * 15 / 16
            val wasFiltered = fill.isAntiAlias
            fill.isAntiAlias = false
            if (shown.isChat) {
                logo(x, top, square * 16)
            } else {
                pixels(LiveBotPixels.body, square, x, top, Color.WHITE)
                pixels(LiveBotPixels.face(face), square, x, top, canvasColor)
            }
            tool?.let {
                val drawing = LiveBotPixels.tool(it)
                pixels(
                    drawing.rects,
                    square,
                    x + 16.6f * square,
                    top + (
                        if (drawing.height >
                            6
                        ) {
                            2
                        } else {
                            3
                        }
                        ) * square,
                    Color.WHITE
                )
            }
            fill.isAntiAlias = wasFiltered
        }

        private fun toolWidth() = shown.tool?.let { (LiveBotPixels.tool(it).width + 1.6f) * BOT / 16 } ?: 0f

        // Roads

        private fun starting() {
            road(BOT + 8, rainbow(0f, BOT + 8), 2f)
            bot(3f)
            dots(BOT + 18, base)
        }

        private fun working() {
            val head = 0.6f * w
            val x0 = head - span(head)
            val paint = rainbow(x0, head, if (shown.isDeep) 0.12f else 1f)
            road(head + 8, paint)
            if (!shown.isDeep) origin(RAINBOW[0])
            drawTicks(head, paint) { _, tick -> !tick.refused }
            drawTicks(head, paint.apply { alpha = 89 }) { _, tick -> tick.refused }
            paint.alpha = 255
            stops(head)
            bot(head + 3)
            dots(head + BOT + toolWidth() + 12, base)
        }

        private fun agents() {
            val fork = 0.42f * w
            val trailEnd = fork - 6
            val paint = rainbow(trailEnd - span(trailEnd), 0.72f * w)
            val end = 0.66f * w
            val lanes =
                if (shown.agentBots <
                    3
                ) {
                    listOf(base - 26, base - 0.5f)
                } else {
                    listOf(base - 35, base - 17.5f, base - 0.5f)
                }
            val faces = listOf(LivePresentation.Face.RIGHT, LivePresentation.Face.DOWN, LivePresentation.Face.FOCUS)
            road(fork, paint)
            origin(RAINBOW[0])
            drawTicks(trailEnd, paint)
            val stroke = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            val fromX = fork - 2
            val fromY = base - 0.5f
            lanes.forEach { lane ->
                val path = Path().apply {
                    moveTo(fromX, fromY)
                    cubicTo(fromX + 20, fromY, fromX + 10, lane, fromX + 30, lane)
                    lineTo(end, lane)
                }
                canvas.drawPath(path, stroke)
            }
            stops(trailEnd)
            lanes.forEachIndexed { index, lane ->
                bot(end + 2, faces[index], null, SMALL_BOT, lane + 0.5f)
                dots(end + SMALL_BOT + 12, lane + 0.5f)
            }
            if (shown.moreAgents > 0) {
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = color(R.color.road_dim)
                    textSize = 11f
                    textAlign = Paint.Align.RIGHT
                    typeface = ResourcesCompat.getFont(context, R.font.geist_mono_medium) ?: Typeface.MONOSPACE
                }
                canvas.drawRect(w - 26, lanes[1] - 9, w, lanes[1] + 9, solid(canvasColor))
                canvas.drawText("+${shown.moreAgents}", w, lanes[1] + 4, text)
            }
        }

        /** Decoration, never a word count. */
        private fun writing() {
            val trailEnd = 0.3f * w
            val paint = rainbow(trailEnd - span(trailEnd), trailEnd)
            val pen = 0.7f * w
            road(trailEnd, paint)
            origin(RAINBOW[0])
            drawTicks(trailEnd, paint)
            for ((y, widths, opacity) in WRITTEN_ROWS) {
                var x = trailEnd + 12
                fill.color = ColorUtils.setAlphaComponent(ink, (opacity * 255).toInt())
                for (width in widths) {
                    if (x + width > pen + 2) break
                    canvas.drawRoundRect(x, base - y - 2.5f, x + width, base - y + 1.5f, 2f, 2f, fill)
                    x += width + 5
                }
            }
            stops(trailEnd)
            bot(pen + 4)
        }

        /** A decision or a question: the bot turns to you and holds up a card with what it's about. */
        private fun yourTurn() {
            val head = 0.34f * w
            val right = head + 4 + BOT
            val card = right + 4
            road(head + 8, solid(line))
            origin(grey)
            drawTicks(head, solid(grey))
            stops(head)
            val dashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = color(R.color.road_dimmed_ahead)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                pathEffect = DashPathEffect(floatArrayOf(4f, 5f), 0f)
            }
            canvas.drawLine(card + 46, base, w, base, dashes)
            bot(head + 4, tool = null)
            canvas.drawRect(right - 2, base - 16, right + 8, base - 13, solid(Color.WHITE))
            heldCard(card, base - 40)
        }

        private fun heldCard(x: Float, y: Float) {
            val centreX = x + CARD_WIDTH / 2
            val centreY = y + CARD_HEIGHT / 2
            canvas.withRotation(-5f, centreX, centreY) {
                val glow = solid(ColorUtils.setAlphaComponent(attention, 89)).apply {
                    maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL)
                }
                drawRoundRect(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 4f, 4f, glow)
                drawRoundRect(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 4f, 4f, solid(attention))
                if (LiveServices.of(shown.service) != null) {
                    val plate = solid(Color.WHITE)
                    drawRoundRect(centreX - 9, centreY - 9, centreX + 9, centreY + 9, 3f, 3f, plate)
                    logo(shown.service, centreX, centreY, 14f, canvasColor)
                } else {
                    val tool =
                        if (shown.state == LivePresentation.State.INPUT) {
                            LivePresentation.Tool.ASK
                        } else {
                            LivePresentation.Tool.WRITE
                        }
                    val drawing = LiveBotPixels.tool(tool)
                    withTranslation(centreX - drawing.width.toFloat(), centreY - drawing.height.toFloat()) {
                        pixels(drawing.rects, 2f, 0f, 0f, canvasColor)
                    }
                }
            }
        }

        private fun finished() {
            val destination = w - 36
            val head = destination - BOT - 8
            road(destination + 2, solid(ColorUtils.setAlphaComponent(positive, 153)))
            origin(positive)
            drawTicks(head - 4, solid(positive))
            stops(head - 4)
            bot(head)
            // The road ends at the logo: where the logo already walks it, once is enough.
            if (!shown.isChat) logo(destination, base - 35, 36f)
        }

        private fun broken() {
            val head = 0.46f * w
            val last = ticks.size - 3
            val crack = head + 3
            road(head, solid(line))
            origin(grey)
            drawTicks(head, solid(grey)) { index, _ -> index < last }
            drawTicks(head, solid(negative)) { index, _ -> index >= last }
            stops(head)
            val y = base + 1
            val path = Path().apply {
                moveTo(crack, y)
                lineTo(crack + 6, y - 8)
                lineTo(crack + 1, y - 13)
                lineTo(crack + 9, y - 24)
            }
            val paint = solid(negative).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
            }
            canvas.drawPath(path, paint)
            bot(crack + 18)
        }

        private fun halted() {
            val head = 0.46f * w
            val cap = head + BOT + 14
            road(head + 8, solid(line))
            origin(grey)
            drawTicks(head, solid(grey))
            stops(head)
            bot(head + 4)
            canvas.drawRoundRect(cap, base - 30, cap + 4, base + 1, 2f, 2f, solid(ink))
        }

        private fun quiet() {
            val head = 0.6f * w
            drawTicks(head, solid(color(R.color.road_quiet)))
            val dashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                pathEffect = DashPathEffect(floatArrayOf(3f, 5f), 0f)
            }
            canvas.drawLine(0f, base, head + 8, base, dashes)
            stops(head)
            bot(head + 3)
        }

        private companion object {
            const val CARD_WIDTH = 34f
            const val CARD_HEIGHT = 27f
            val WRITTEN_ROWS = listOf(
                Triple(24f, listOf(26f, 14f, 30f, 18f, 24f, 38f, 16f), 0.45f),
                Triple(12f, listOf(20f, 34f, 12f, 26f, 22f, 40f), 0.7f),
                Triple(0f, listOf(34f, 16f, 24f, 30f, 40f, 18f), 0.95f)
            )
        }
    }
}
