package com.chatwithwork.app.push

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.chatwithwork.app.R
import java.io.ByteArrayOutputStream

/**
 * A Live Update in Android 16's own template, the one the system promotes:
 * a chip in the status bar (the bot mark's face and a word,
 * [LivePresentation.chip]), the top of the lock screen and the shade, and
 * whatever a phone maker builds on promoted notifications (OnePlus and OPPO's
 * Live Alerts capsule, Samsung's Now Bar, Xiaomi's island).
 *
 * The system promotes only an ongoing notification that asks for it, with a
 * title, a standard style, no custom views and no colorized background
 * (`Notification.hasPromotableCharacteristics`), so the drawn card
 * ([LiveCard]) stays below Android 16, where nothing is promoted.
 *
 * Laid out like the best of them (a ride's car on its way): a bold line of
 * what it's doing, the steps under it, the road with the bot walking it, and
 * the bot's face, on its own paper, as the picture on the right. The road is
 * [LivePresentation.road]: a segment per step, as long as the time it took,
 * the bot always at the same place with the open road ahead, so it never
 * reads as how far along it is. A wait for the person turns the road ahead
 * amber and says so in a chip; a finished road is green and walked to its
 * end; a failed or stopped one is flat grey. In a person's chat Chat with
 * Work's mark walks it, and there's no picture: the header has the logo.
 */
object LiveTemplate {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    val promotes: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    /** The picture beside the words, in pixels: Android shows it at 48dp at most. */
    private const val PICTURE_PX = 192

    /** Room left at each side of the picture, so the bot fills it as a ride's driver photo does, never clipped. */
    private const val PICTURE_INSET = 0.03f

    /** The mark's 16 by 16 grid, and the middle of its bubble's rows (2 to 15), which the drawing centres on. */
    private const val GRID = 16
    private const val BODY_MIDDLE = 8.5f

    /** The bot on the road: Android draws the tracker at most 24dp tall. */
    private const val TRACKER_PX = 96

    fun dress(
        builder: NotificationCompat.Builder,
        context: Context,
        shown: LivePresentation,
        title: String,
        text: String?,
        open: PendingIntent
    ): NotificationCompat.Builder {
        val road = shown.road
        builder.setStyle(
            NotificationCompat.ProgressStyle()
                .setProgressSegments(
                    road.stretches.map {
                        NotificationCompat.ProgressStyle.Segment(it.length).setColor(color(context, it.tone))
                    }
                )
                .setProgress(road.at)
                .setProgressTrackerIcon(IconCompat.createFromIcon(context, tracker(context, shown)))
        )
        builder.setContentTitle(bold(title))
        builder.setContentText(subtitle(context, shown, text))
        if (!shown.isChat) {
            builder.setLargeIcon(
                mark(
                    context,
                    shown.mark.sticker,
                    ContextCompat.getColor(context, shown.paperColor),
                    PICTURE_PX,
                    PICTURE_INSET
                )
            )
        }
        shown.chip?.let { builder.setShortCriticalText(context.getString(it)) }
        shown.action?.let { builder.addAction(0, context.getString(it), open) }
        return builder.setSmallIcon(shown.templateIcon)
    }

    private fun color(context: Context, tone: LivePresentation.Tone): Int = ContextCompat.getColor(
        context,
        when (tone) {
            LivePresentation.Tone.WALKED, LivePresentation.Tone.AHEAD -> R.color.lw_live_ink
            LivePresentation.Tone.WAITING -> R.color.lw_attention_ink
            LivePresentation.Tone.ARRIVED -> R.color.lw_positive_ink
            LivePresentation.Tone.REFUSED, LivePresentation.Tone.FLAT -> R.color.lw_ink_muted
        }
    )

    private fun tracker(context: Context, shown: LivePresentation): Icon = if (shown.isChat) {
        Icon.createWithResource(context, shown.tracker)
    } else {
        mark(context, shown.tracker, ContextCompat.getColor(context, shown.paperColor), TRACKER_PX, 0f)
    }

    private fun bold(title: String): CharSequence = SpannableStringBuilder(title).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * The steps, after what it waits for when it waits for the person. Plain
     * words: Android 16 drops colour from a promoted notification's text, so
     * the amber is the road ahead's and the status bar chip's word.
     */
    private fun subtitle(context: Context, shown: LivePresentation, text: String?): String? {
        // Under a chat's title, the line already starts with what it waits for.
        if (!shown.isWaiting || shown.chatTitle != null) return text
        val steps = shown.walked.takeIf { it > 0 }?.let {
            context.resources.getQuantityString(R.plurals.live_update_steps, it, it)
        }
        return listOfNotNull(context.getString(R.string.live_update_waiting_for_you), steps).joinToString(" · ")
    }

    /**
     * The bot mark drawn on its paper: the sticker (white body, black lines),
     * its white multiplied by the bot's colour, so the lines stay black. Whole
     * pixels per square, so its edges stay sharp, and its bubble centred, as
     * the grid's top rows are empty. As PNG data, a few hundred bytes, never a
     * bitmap in the notification.
     */
    private fun mark(context: Context, @DrawableRes sticker: Int, paper: Int, size: Int, inset: Float): Icon {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val square = (size * (1 - 2 * inset) / GRID).toInt()
        val left = (size - square * GRID) / 2
        val top = (size / 2f - BODY_MIDDLE * square).toInt()
        ContextCompat.getDrawable(context, sticker)?.apply {
            setBounds(left, top, left + square * GRID, top + square * GRID)
            draw(canvas)
        }
        canvas.drawRect(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            Paint().apply {
                color = paper
                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
        )
        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
        bitmap.recycle()
        return Icon.createWithData(png.toByteArray(), 0, png.size())
    }
}
