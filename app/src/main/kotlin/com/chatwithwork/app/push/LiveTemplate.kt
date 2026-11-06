package com.chatwithwork.app.push

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.chatwithwork.app.R

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
 * The bar is the road, never progress: a stretch per step, as long as the time
 * it took, and the bot's face at its head. Before the first step it's the
 * system's moving bar. A refused step, and the whole road once it failed or
 * stopped, is flat grey.
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
        val flat = shown.state in FLAT
        val ink = ContextCompat.getColor(context, if (flat) R.color.lw_ink_muted else shown.color)
        val muted = ContextCompat.getColor(context, R.color.lw_ink_muted)
        val stretches = shown.stretches
        val style =
            NotificationCompat.ProgressStyle()
                .setProgressTrackerIcon(IconCompat.createWithResource(context, shown.templateTracker))
        if (stretches.isEmpty()) {
            style.setProgressIndeterminate(shown.isWorking)
        } else {
            style.setProgressSegments(
                stretches.map {
                    NotificationCompat.ProgressStyle.Segment(it.length).setColor(if (it.refused) muted else ink)
                }
            )
            style.setProgress(stretches.sumOf { it.length })
        }
        shown.chip?.let { builder.setShortCriticalText(context.getString(it)) }
        shown.action?.let { builder.addAction(0, context.getString(it), open) }
        return builder
            .setSmallIcon(shown.templateIcon)
            .setStyle(style)
    }

    private val FLAT =
        setOf(LivePresentation.State.FAILED, LivePresentation.State.STOPPED, LivePresentation.State.QUIET)
}
