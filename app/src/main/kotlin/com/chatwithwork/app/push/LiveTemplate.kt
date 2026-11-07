package com.chatwithwork.app.push

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

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
 * While it works the bar is the live wire: the system's moving bar in the live
 * colour, which says only that work is happening now, never how far along it
 * is, since nobody knows. A wait for the person or an end has no bar at all:
 * the words, the face and the colour say it. The bot's face, as a sticker,
 * sits beside the words; a person's chat has Chat with Work's logo in the
 * header already and no face, so it shows none.
 */
object LiveTemplate {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    val promotes: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    fun dress(
        builder: NotificationCompat.Builder,
        context: Context,
        shown: LivePresentation,
        open: PendingIntent
    ): NotificationCompat.Builder {
        if (shown.isWorking) {
            // An indeterminate bar takes its first segment's colour.
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(
                        listOf(
                            NotificationCompat.ProgressStyle.Segment(1)
                                .setColor(ContextCompat.getColor(context, shown.color))
                        )
                    )
                    .setProgressIndeterminate(true)
            )
        }
        shown.sticker?.let { builder.setLargeIcon(Icon.createWithResource(context, it)) }
        shown.chip?.let { builder.setShortCriticalText(context.getString(it)) }
        shown.action?.let { builder.addAction(0, context.getString(it), open) }
        return builder.setSmallIcon(shown.templateIcon)
    }
}
