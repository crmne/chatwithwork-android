package com.chatwithwork.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.chatwithwork.app.BuildConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.main.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Push notifications through Firebase Cloud Messaging.
 *
 * Nothing talks to Firebase until the person turns notifications on: token
 * auto-init is off in the manifest and only switched on by [token]. Builds
 * without google-services.json (forks, CI) have no Firebase at all and report
 * push as unavailable. See docs/push-notifications.md.
 */
object PushNotifications {
    const val CHANNEL_APPROVALS = "approvals"

    private const val PREFERENCES = "push"
    private const val KEY_ASKED = "asked_for_permission"
    private const val KEY_OPTED_IN = "opted_in"
    private const val KEY_TOKEN = "token"

    /** The permission, with the names the iOS app uses for the same states. */
    enum class Permission(val web: String) {
        GRANTED("authorized"),
        DENIED("denied"),
        NOT_ASKED("not_determined")
    }

    fun initialize(context: Context) {
        createChannels(context)
    }

    /** Whether this build can receive pushes at all. */
    fun isAvailable(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Where push stands for this person on this phone. Android 12 and earlier
     * allow notifications without asking, so "granted" also needs the person
     * to have turned push on in the app (opted in): nobody is registered with
     * Firebase by default.
     */
    fun permission(context: Context): Permission = when {
        !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
            if (needsRuntimePermission(context) && !askedBefore(context)) Permission.NOT_ASKED else Permission.DENIED

        optedIn(context) -> Permission.GRANTED

        else -> Permission.NOT_ASKED
    }

    fun optedIn(context: Context): Boolean = preferences(context).getBoolean(KEY_OPTED_IN, false)

    /** The person turned push on (the page's "Turn on", `get`). */
    fun optIn(context: Context) {
        preferences(context).edit { putBoolean(KEY_OPTED_IN, true) }
    }

    /** Whether the app still needs Android 13's notification permission. */
    fun needsRuntimePermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    fun markAsked(context: Context) {
        preferences(context).edit { putBoolean(KEY_ASKED, true) }
    }

    /**
     * The device's FCM token, registering with Firebase (and turning on
     * auto-init) if there isn't one yet. Null when push isn't available or
     * Firebase doesn't answer in time.
     */
    suspend fun token(context: Context): String? {
        if (!isAvailable(context)) return null
        lastToken(context)?.let { return it }

        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        val registered = CompletableDeferred<String>()
        waiting += registered
        // FCM 25.1 delivers the token to PushMessagingService.onRegistered.
        messaging.register()
        return withTimeoutOrNull(REGISTRATION_TIMEOUT_MS) { registered.await() }
            .also { waiting -= registered }
    }

    /** The last token Firebase gave us. */
    fun lastToken(context: Context): String? = preferences(context).getString(KEY_TOKEN, null)

    /** Called by the messaging service when Firebase hands over a (new) token. */
    fun remember(context: Context, token: String) {
        preferences(context).edit { putString(KEY_TOKEN, token) }
        waiting.toList().forEach { it.complete(token) }
    }

    /**
     * Forgets this device's token when the session ends, so pushes for it
     * stop at Firebase too. The next sign-in registers anew.
     */
    fun forget(context: Context) {
        val hadToken = lastToken(context) != null
        preferences(context).edit {
            remove(KEY_TOKEN)
            remove(KEY_OPTED_IN)
        }
        if (!hadToken || !isAvailable(context)) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        messaging.unregister()
    }

    /**
     * Shows a push as a notification, one per chat (its thread), a newer one
     * replacing it; a `resolved` push takes it away instead. Tapping it opens
     * the push's page in the app.
     */
    fun deliver(context: Context, push: Push) {
        val manager = NotificationManagerCompat.from(context)
        if (push.kind == Push.Kind.RESOLVED) {
            manager.cancel(push.thread, NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val body = push.body ?: context.getString(push.defaultBody)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_APPROVALS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(push.title ?: context.getString(R.string.app_name))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, push.url))
                .build()
        manager.notify(push.thread, NOTIFICATION_ID, notification)
    }

    private fun openIntent(context: Context, url: String): PendingIntent {
        val intent =
            Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private const val NOTIFICATION_ID = 1

    private val waiting = java.util.concurrent.CopyOnWriteArrayList<CompletableDeferred<String>>()
    private const val REGISTRATION_TIMEOUT_MS = 15_000L

    private fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPROVALS,
                context.getString(R.string.channel_approvals),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_approvals_description) }
        )
    }

    private fun askedBefore(context: Context) = preferences(context).getBoolean(KEY_ASKED, false)

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
