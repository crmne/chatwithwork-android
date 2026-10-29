package com.chatwithwork.app.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.chatwithwork.app.main.MainActivity
import com.chatwithwork.app.routing.AppUrls
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A Live Update for a chat the person left while it was answering: an
 * ongoing native status card (promoted on Android 16 where the system allows,
 * as OEM skins like ColorOS's and
 * OxygenOS's Live Alerts do for apps that use it), saying the organization's
 * name and what the answer is doing, never the chat's title or anything in it.
 *
 * The app asks the server to follow the chat ([follow]), which then sends
 * each step as FCM data of kind `progress` ([progress]): "Searching Todoist",
 * the step's word in the status bar chip, and how many steps it took. The
 * end comes the same way, and the Live Update becomes a quiet notification
 * saying how it ended. It shares the chat's notification slot (its thread as
 * the tag), so the push that comes when the answer is ready, waits for the
 * person, or fails takes its place, and `resolved` clears it. It goes when
 * the person comes back, and on its own after [TIMEOUT_MS] if nothing ends it.
 */
object LiveUpdates {
    private const val TIMEOUT_MS = 30 * 60 * 1000L
    private const val ENDED_SHOWN_MS = 15 * 60 * 1000L
    private const val FOLLOW_TIMEOUT_MS = 10_000

    /** When the server sent the step a Live Update shows, kept in its extras. */
    private const val EXTRA_SENT_AT = "com.chatwithwork.app.LIVE_UPDATE_SENT_AT"

    /** The organization a Live Update names, kept in its extras for the steps that follow. */
    private const val EXTRA_ORGANIZATION = "com.chatwithwork.app.LIVE_UPDATE_ORGANIZATION"
    private const val PREFERENCES = "live_updates"

    /** The push thread for a chat's page ("chat-482139075-42"), as the server names it. */
    fun threadFor(url: String): String? {
        val account = AppUrls.account(url) ?: return null
        val number = AppUrls.chatNumber(url) ?: return null
        return "chat-$account-$number"
    }

    @Synchronized
    fun show(context: Context, url: String, organization: String?) {
        val thread = threadFor(url) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!PushNotifications.optedIn(context)) return

        val notification =
            ongoing(context, url, organization ?: context.getString(R.string.app_name), System.currentTimeMillis())
                .setContentTitle(context.getString(R.string.live_update_answering))
                .setShortCriticalText(context.getString(R.string.live_update_chip_think))
                .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, lastSentAt(context, thread)) })
                .build()
        NotificationManagerCompat.from(context).notify(thread, PushNotifications.NOTIFICATION_ID, notification)
    }

    /**
     * The server's step, or the end, for a chat whose Live Update still
     * shows; nothing for one the person came back to, or a push replaced.
     */
    @Synchronized
    fun progress(context: Context, progress: LiveProgress) {
        if (progress.sentAt <= lastSentAt(context, progress.thread)) return
        // Keep the server watermark after returning to a chat or an end push.
        // Following the same chat again must not admit that old end a second time.
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putLong(progress.thread, progress.sentAt)
        }
        val manager = NotificationManagerCompat.from(context)
        val showing =
            manager.activeNotifications.firstOrNull {
                it.tag == progress.thread &&
                    it.id == PushNotifications.NOTIFICATION_ID &&
                    it.notification.channelId == PushNotifications.CHANNEL_ANSWERING &&
                    it.isOngoing
            } ?: return
        val shown = showing.notification
        if (progress.sentAt <= shown.extras.getLong(EXTRA_SENT_AT, 0)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val organization = shown.extras.getString(EXTRA_ORGANIZATION) ?: context.getString(R.string.app_name)
        val notification =
            if (progress.isWorking) {
                ongoing(context, progress.url, organization, shown.`when`, progress.step, progress.steps)
                    .setContentTitle(progress.message ?: context.getString(R.string.live_update_text))
                    .setShortCriticalText(context.getString(chipWord(progress.step)))
                    .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, progress.sentAt) })
                    .build()
            } else {
                ended(context, progress, organization)
            }
        manager.notify(progress.thread, PushNotifications.NOTIFICATION_ID, notification)
    }

    private fun lastSentAt(context: Context, thread: String): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(thread, 0)

    /** A signed-out identity must leave no per-chat push state on this install. */
    fun forget(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { clear() }
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

    /**
     * Asks the server to send this phone the answer's steps and its end
     * (`POST /:account/chats/:number/live_activity`, platform android), with
     * the web view's session and the page's CSRF token. Fire and forget: a
     * Live Update the server doesn't hear of still shows, without steps.
     */
    fun follow(context: Context, url: String, csrfToken: String?) {
        val token = PushNotifications.lastToken(context) ?: return
        val endpoint = url.substringBefore('?').substringBefore('#').trimEnd('/') + "/live_activity"
        val body =
            buildJsonObject {
                putJsonObject("live_activity") {
                    put("token", token)
                    put("app_id", context.packageName)
                    put("environment", "production")
                    put("platform", "android")
                }
            }.toString()
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

        thread(name = "live-update-follow") {
            val connection =
                try {
                    URL(endpoint).openConnection() as HttpURLConnection
                } catch (_: IOException) {
                    return@thread
                }
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = FOLLOW_TIMEOUT_MS
                connection.readTimeout = FOLLOW_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                csrfToken?.let { connection.setRequestProperty("X-CSRF-Token", it) }
                cookie?.let { connection.setRequestProperty("Cookie", it) }
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.responseCode
            } catch (_: IOException) {
                // Nothing to do: the Live Update shows without steps.
            } finally {
                connection.disconnect()
            }
        }
    }

    /** The status bar chip's word for a step: short, so it shows whole. */
    fun chipWord(step: String?): Int = when (step) {
        "search" -> R.string.live_update_chip_search
        "list" -> R.string.live_update_chip_list
        "read" -> R.string.live_update_chip_read
        "use" -> R.string.live_update_chip_use
        "plan" -> R.string.live_update_chip_plan
        "run" -> R.string.live_update_chip_run
        "ask" -> R.string.live_update_chip_ask
        "write" -> R.string.live_update_chip_write
        else -> R.string.live_update_chip_think
    }

    private fun stepsText(context: Context, steps: Int): String? =
        if (steps > 0) context.resources.getQuantityString(R.plurals.live_update_steps, steps, steps) else null

    /** Native BigTextStyle remains eligible for promotion, without implying a known percentage. */
    private fun ongoing(
        context: Context,
        url: String,
        organization: String,
        startedAt: Long,
        step: String? = null,
        steps: Int = 0
    ): NotificationCompat.Builder = NotificationCompat
        .Builder(context, PushNotifications.CHANNEL_ANSWERING)
        .setSmallIcon(R.drawable.ic_notification)
        .setSubText(organization)
        .setContentText(stepsText(context, steps) ?: context.getString(R.string.live_update_background))
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                stepsText(context, steps) ?: context.getString(R.string.live_update_background)
            )
        )
        .setLargeIcon(statusIcon(context, LivePresentation(LiveProgress.ANSWERING, step)))
        .setColor(ContextCompat.getColor(context, R.color.lw_live_ink))
        .addExtras(Bundle().apply { putString(EXTRA_ORGANIZATION, organization) })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setRequestPromotedOngoing(true)
        .setUsesChronometer(true)
        .setWhen(startedAt)
        .setShowWhen(true)
        .setTimeoutAfter(TIMEOUT_MS)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(open(context, url))
        .addAction(0, context.getString(R.string.live_update_open), open(context, url))

    /** How it ended, quietly in the chat's slot until a push takes it or the person opens it. */
    private fun ended(context: Context, progress: LiveProgress, organization: String): Notification {
        val presentation = LivePresentation(progress.phase)
        return NotificationCompat.Builder(context, PushNotifications.CHANNEL_ANSWERING)
            .setSmallIcon(R.drawable.ic_notification)
            .setSubText(organization)
            .setContentTitle(context.getString(presentation.headline))
            .setContentText(progress.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progress.message))
            .setLargeIcon(statusIcon(context, presentation))
            .setColor(ContextCompat.getColor(context, presentation.color))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(ENDED_SHOWN_MS)
            .setContentIntent(open(context, progress.url))
            .addAction(0, context.getString(presentation.action), open(context, progress.url))
            .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, progress.sentAt) })
            .build()
    }

    /** A real state icon, never a decorative progress gauge with a made-up total. */
    private fun statusIcon(context: Context, presentation: LivePresentation): Bitmap {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val color = ContextCompat.getColor(context, presentation.color)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 3f
        canvas.drawRoundRect(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = ColorUtils.setAlphaComponent(color, 32)
            }
        )
        SymbolDrawable(context, presentation.symbol, sizeDp = 26f, color = color).apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        return bitmap
    }

    private fun open(context: Context, url: String): PendingIntent = PendingIntent.getActivity(
        context,
        url.hashCode(),
        Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
