package com.chatwithwork.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.chatwithwork.app.R
import com.chatwithwork.app.main.MainActivity
import com.chatwithwork.app.routing.AppUrls

/**
 * A Live Update for a chat the person left while it was answering: an
 * ongoing notification with indeterminate progress (Android 16's progress
 * style, promoted where the system allows), saying only "Answering" and the
 * organization's name, never the chat's title or anything in it. It shares
 * the chat's notification slot (its thread as the tag), so the push that
 * comes when the answer is ready, waits for the person, or fails takes its
 * place, and `resolved` clears it. It goes when the person comes back, and
 * on its own after [TIMEOUT_MS] if no push ever comes.
 */
object LiveUpdates {
    private const val TIMEOUT_MS = 30 * 60 * 1000L

    /** The push thread for a chat's page ("chat-482139075-42"), as the server names it. */
    fun threadFor(url: String): String? {
        val account = AppUrls.account(url) ?: return null
        val number = AppUrls.chatNumber(url) ?: return null
        return "chat-$account-$number"
    }

    fun show(context: Context, url: String, organization: String?) {
        val thread = threadFor(url) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!PushNotifications.optedIn(context)) return

        val open =
            PendingIntent.getActivity(
                context,
                url.hashCode(),
                Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        val notification =
            NotificationCompat
                .Builder(context, PushNotifications.CHANNEL_ANSWERING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(organization ?: context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.live_update_text))
                .setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(context.getString(R.string.live_update_answering))
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis())
                .setTimeoutAfter(TIMEOUT_MS)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(context).notify(thread, PushNotifications.NOTIFICATION_ID, notification)
    }

    /** Back in the chat: its Live Update goes (a push that already replaced it stays). */
    fun cancel(context: Context, thread: String) {
        val manager = NotificationManagerCompat.from(context)
        val ongoing =
            manager.activeNotifications.any {
                it.tag == thread && it.notification.channelId == PushNotifications.CHANNEL_ANSWERING
            }
        if (ongoing) manager.cancel(thread, PushNotifications.NOTIFICATION_ID)
    }
}
