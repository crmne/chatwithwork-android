package com.chatwithwork.app.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.chatwithwork.app.R
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
 * ongoing card ([LiveCard], the iOS app's own, on every Android version),
 * saying the organization's name and what the answer is doing, never the
 * chat's title or anything in it.
 *
 * The app asks the server to follow the chat ([follow]), which then sends
 * each step as FCM data of kind `progress` ([progress]): "Searching Todoist",
 * the step's word in the status bar chip, and how many steps it took. The
 * end comes the same way, and the Live Update becomes a quiet notification
 * saying how it ended. It shares the chat's notification slot (its thread as
 * the tag), so the push that comes when the answer is ready or fails takes
 * its place. A wait for the person shows on the same card, which stays
 * ongoing (its push goes beside it, [PushNotifications.deliver]). It goes when
 * the person comes back, and on its own after [TIMEOUT_MS] if nothing ends it.
 *
 * [LiveRuns] remembers each chat's run apart from its card: the timer is the
 * answer's own, whatever came and went, and once the person dismisses a card
 * that run shows none again, while a later answer does.
 */
object LiveUpdates {
    private const val TIMEOUT_MS = 30 * 60 * 1000L
    private const val ENDED_SHOWN_MS = 15 * 60 * 1000L
    private const val FOLLOW_TIMEOUT_MS = 10_000
    private const val STARTING_MS = 15_000L

    /** When the server sent the step a Live Update shows, kept in its extras. */
    private const val EXTRA_SENT_AT = "com.chatwithwork.app.LIVE_UPDATE_SENT_AT"

    /** The organization a Live Update names, kept in its extras for the steps that follow. */
    private const val EXTRA_ORGANIZATION = "com.chatwithwork.app.LIVE_UPDATE_ORGANIZATION"
    private const val EXTRA_STEPS = "com.chatwithwork.app.LIVE_UPDATE_STEPS"
    private const val EXTRA_RUN = "com.chatwithwork.app.LIVE_UPDATE_RUN"
    private const val EXTRA_TRAIL = "com.chatwithwork.app.LIVE_UPDATE_TRAIL"
    private const val EXTRA_PACE = "com.chatwithwork.app.LIVE_UPDATE_PACE"
    private const val EXTRA_SERVICES = "com.chatwithwork.app.LIVE_UPDATE_SERVICES"
    private const val EXTRA_STARTED_AT = "com.chatwithwork.app.LIVE_UPDATE_STARTED_AT"
    private const val PREFERENCES = "live_updates"

    /** Goes up as the session ends, so a request begun before can't act after it. */
    @Volatile
    private var session = 0

    /** The push thread for a chat's page ("chat-482139075-42"), as the server names it. */
    fun threadFor(url: String): String? {
        val account = AppUrls.account(url) ?: return null
        val number = AppUrls.chatNumber(url) ?: return null
        return "chat-$account-$number"
    }

    /**
     * The chat's card as the person leaves it. False when they dismissed this
     * run's card, so nothing should follow it.
     */
    @Synchronized
    fun show(context: Context, url: String, organization: String?, run: LiveRunRef? = null): Boolean {
        val thread = threadFor(url) ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        if (!PushNotifications.optedIn(context)) return true

        val now = System.currentTimeMillis()
        val startedAt = if (run != null) runs(context).starting(thread, run, now) ?: return false else now
        // A card shown again partway through an answer isn't starting, and hears its steps soon.
        val step = "answer".takeIf { now - startedAt < STARTING_MS }
        val notification =
            ongoing(
                context,
                url,
                organization ?: context.getString(R.string.app_name),
                LivePresentation(LiveProgress.ANSWERING, step),
                null,
                startedAt,
                startedAt
            )
                .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, lastSentAt(context, thread)) })
                .tracking(context, thread, run?.id, now)
                .build()
        NotificationManagerCompat.from(context).notify(thread, PushNotifications.NOTIFICATION_ID, notification)
        return true
    }

    /**
     * The server's step, or the end, for a chat whose Live Update still
     * shows; nothing for one the person came back to, or a push replaced,
     * nor for one that's about another answer or older than what it shows.
     */
    @Synchronized
    fun progress(context: Context, progress: LiveProgress) {
        val run = progress.run
        if (run == null && progress.sentAt <= lastSentAt(context, progress.thread)) return
        val manager = NotificationManagerCompat.from(context)
        val showing =
            manager.activeNotifications.firstOrNull {
                it.tag == progress.thread &&
                    it.id == PushNotifications.NOTIFICATION_ID &&
                    it.notification.channelId == PushNotifications.CHANNEL_ANSWERING &&
                    it.isOngoing
            }
        if (run != null) {
            val unbound = showing != null && showing.notification.extras.getString(EXTRA_RUN) == null
            val admitted = runs(context).admit(progress.thread, run, progress, unbound)
            if (admitted != LiveRuns.Admission.SHOW) return
        }
        // Keep the server watermark after returning to a chat or an end push.
        // Following the same chat again must not admit that old end a second time.
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putLong(progress.thread, progress.sentAt)
        }
        if (showing == null) return
        val shown = showing.notification
        if (run == null && progress.sentAt <= shown.extras.getLong(EXTRA_SENT_AT, 0)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val organization = shown.extras.getString(EXTRA_ORGANIZATION) ?: context.getString(R.string.app_name)
        val startedAt =
            run?.let { runs(context).record(progress.thread)?.startedAt }
                ?: shown.extras.getLong(EXTRA_STARTED_AT, shown.`when`)
        val state =
            LivePresentation(
                progress.phase,
                progress.step,
                maxOf(progress.steps, if (progress.isEnd) shown.extras.getInt(EXTRA_STEPS, 0) else 0),
                progress.trail ?: shown.extras.getString(EXTRA_TRAIL),
                progress.service,
                progress.services.ifEmpty { shown.extras.getStringArrayList(EXTRA_SERVICES).orEmpty() },
                progress.agents,
                if (progress.trail != null) progress.pace else shown.extras.getString(EXTRA_PACE)
            )
        val notification =
            if (!progress.isEnd) {
                ongoing(
                    context,
                    progress.url,
                    organization,
                    state,
                    progress.message,
                    startedAt,
                    progress.waitingSince?.takeIf { progress.isWaiting } ?: startedAt
                )
                    .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, progress.sentAt) })
                    .tracking(context, progress.thread, run, System.currentTimeMillis())
                    .build()
            } else {
                ended(
                    context,
                    progress,
                    state,
                    organization,
                    progress.heardAt?.let { it - (progress.startedAt ?: startedAt) }
                )
            }
        manager.notify(progress.thread, PushNotifications.NOTIFICATION_ID, notification)
    }

    /** The system took a working card away: unless it timed out, the person dismissed that run. */
    @Synchronized
    fun dismissed(context: Context, thread: String, run: String, postedAt: Long) {
        runs(context).dismissed(thread, run, postedAt, System.currentTimeMillis(), TIMEOUT_MS)
    }

    private fun runs(context: Context): LiveRuns {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return LiveRuns({
            preferences.getString(it, null)
        }, { key, value -> preferences.edit { putString(key, value) } })
    }

    /** Names the run on its card, and has Android say if the person clears it. */
    private fun NotificationCompat.Builder.tracking(
        context: Context,
        thread: String,
        run: String?,
        postedAt: Long
    ): NotificationCompat.Builder {
        if (run == null) return this
        val intent =
            Intent(context, LiveUpdateDismissed::class.java)
                .putExtra(LiveUpdateDismissed.EXTRA_THREAD, thread)
                .putExtra(LiveUpdateDismissed.EXTRA_RUN, run)
                .putExtra(LiveUpdateDismissed.EXTRA_POSTED_AT, postedAt)
        return addExtras(Bundle().apply { putString(EXTRA_RUN, run) })
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    thread.hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
    }

    private fun lastSentAt(context: Context, thread: String): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(thread, 0)

    /**
     * A signed-out identity must leave no per-chat push state on this
     * install, no card, and no request that could act for it later.
     */
    @Synchronized
    fun forget(context: Context) {
        session++
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { clear() }
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications
            .filter { it.notification.channelId == PushNotifications.CHANNEL_ANSWERING }
            .forEach { manager.cancel(it.tag, it.id) }
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
     * Live Update the server doesn't hear of still shows, without steps. The
     * one exception is [run] being over by the time the server hears (409),
     * when nothing would ever end its card.
     */
    fun follow(context: Context, url: String, csrfToken: String?, run: String? = null) {
        val token = PushNotifications.lastToken(context) ?: return
        val endpoint = url.substringBefore('?').substringBefore('#').trimEnd('/') + "/live_activity"
        val body =
            buildJsonObject {
                putJsonObject("live_activity") {
                    put("token", token)
                    put("app_id", context.packageName)
                    put("environment", "production")
                    put("platform", "android")
                    run?.let { put("run", it) }
                }
            }.toString()
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        val followedIn = session

        thread(name = "live-update-follow") {
            if (followedIn != session) return@thread
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
                if (connection.responseCode == HttpURLConnection.HTTP_CONFLICT && run != null &&
                    followedIn == session
                ) {
                    threadFor(url)?.let { over(context, it, run) }
                }
            } catch (_: IOException) {
                // Nothing to do: the Live Update shows without steps.
            } finally {
                connection.disconnect()
            }
        }
    }

    /** The server says [run] is over, and this card hasn't heard how: it goes, unless a newer run took the slot. */
    @Synchronized
    private fun over(context: Context, thread: String, run: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications
            .filter {
                it.tag == thread && it.isOngoing && it.notification.channelId == PushNotifications.CHANNEL_ANSWERING &&
                    it.notification.extras.getString(EXTRA_RUN) == run
            }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun stepsText(context: Context, steps: Int): String? =
        if (steps > 0) context.resources.getQuantityString(R.plurals.live_update_steps, steps, steps) else null

    /** An ongoing card, its clock running from [clock]. */
    private fun ongoing(
        context: Context,
        url: String,
        organization: String,
        shown: LivePresentation,
        message: String?,
        startedAt: Long,
        clock: Long
    ): NotificationCompat.Builder {
        val starting = shown.state == LivePresentation.State.STARTING
        val waiting = shown.state == LivePresentation.State.DECISION || shown.state == LivePresentation.State.INPUT
        val text = when {
            starting -> organization
            waiting -> context.getString(R.string.live_update_waiting)
            else -> stepsText(context, shown.walked)
        }
        return card(context, url, shown, message, text, organization, LiveCard.Clock.Running(clock))
            .addExtras(
                Bundle().apply {
                    putString(EXTRA_ORGANIZATION, organization)
                    putLong(EXTRA_STARTED_AT, startedAt)
                }
            )
            .setOngoing(true)
            .setWhen(clock)
            .setShowWhen(false)
            .setTimeoutAfter(TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open(context, url))
    }

    /**
     * How it ended, quietly in the chat's slot until a push takes it or the
     * person opens it. An end message says only how it ended, so the road is
     * what the card last showed, and the time is the server's, fixed.
     */
    private fun ended(
        context: Context,
        progress: LiveProgress,
        shown: LivePresentation,
        organization: String,
        took: Long?
    ): Notification {
        val timed = shown.state == LivePresentation.State.FINISHED || shown.state == LivePresentation.State.STOPPED
        val total = took?.takeIf { it >= 0 && timed }
        val clock = total?.let { LiveCard.Clock.Fixed(it) } ?: LiveCard.Clock.Hidden
        return card(context, progress.url, shown, null, stepsText(context, shown.walked), organization, clock)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setTimeoutAfter(ENDED_SHOWN_MS)
            .setContentIntent(open(context, progress.url))
            .addExtras(Bundle().apply { putLong(EXTRA_SENT_AT, progress.sentAt) })
            .build()
    }

    /** What every state of a card shares: its words for the system, its colour and its drawn views. */
    private fun card(
        context: Context,
        url: String,
        shown: LivePresentation,
        message: String?,
        text: String?,
        organization: String,
        clock: LiveCard.Clock
    ): NotificationCompat.Builder {
        val title = shown.title(message, context::getString)
        val view =
            LiveCard(context, shown, title, organization, stepsText(context, shown.walked), clock, open(context, url))
        return NotificationCompat
            .Builder(context, PushNotifications.CHANNEL_ANSWERING)
            .setSmallIcon(shown.face.chip)
            .setSubText(organization)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(ContextCompat.getColor(context, shown.color))
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view.collapsed())
            .setCustomBigContentView(view.expanded())
            .addExtras(
                Bundle().apply {
                    putInt(EXTRA_STEPS, shown.steps)
                    putString(EXTRA_TRAIL, shown.trail)
                    putString(EXTRA_PACE, shown.pace)
                    putStringArrayList(EXTRA_SERVICES, ArrayList(shown.services))
                }
            )
    }

    private fun open(context: Context, url: String): PendingIntent = PendingIntent.getActivity(
        context,
        url.hashCode(),
        Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
