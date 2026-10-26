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
 * Nothing talks to Firebase until the person turns notifications on (the
 * offer the app makes once, when a page asks, or Settings' Turn on): token
 * auto-init is off in the manifest and only switched on by [token]. Builds
 * without google-services.json (forks, CI) have no Firebase at all and report
 * push as unavailable. See docs/push-notifications.md.
 *
 * A push carries no chat content: [receive] fetches the chat's words from
 * the server with the phone's notification key ([keepKey], which the page
 * hands over after registering the token), and shows the push's own words
 * if that fails. The words are private on the lock screen: a phone set to
 * hide sensitive content shows the public version, which says only what
 * happened.
 */
object PushNotifications {
    const val CHANNEL_APPROVALS = "approvals"
    const val CHANNEL_ANSWERS = "answers"
    const val CHANNEL_ANSWERING = "answering"

    private const val PREFERENCES = "push"
    private const val KEY_ASKED = "asked_for_permission"
    private const val KEY_OPTED_IN = "opted_in"

    // Named for the offer after signing in it replaced, so installs that saw that one aren't asked again.
    private const val KEY_OFFERED = "offered_after_sign_in"
    private const val KEY_TOKEN = "token"
    private const val KEY_NOTIFICATION_KEY = "notification_key"
    private const val KEY_NOTIFICATION_KEY_TOKEN = "notification_key_token"

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

    /** The person turned push on: allowed notifications when the app asked, or the page's "Turn on" (`get`). */
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
     * Whether to offer notifications now: once per install, the first time a
     * page says it's worth it (a change waits for approval, or an answer
     * takes a while; the `notification-token` component's `offer`), while
     * nobody has decided yet. [rationale] is Android's
     * shouldShowRequestPermissionRationale, true after the person said no
     * once. Signing out keeps the offer made. Installs that got the old offer
     * after signing in count as offered.
     */
    fun shouldOffer(context: Context, rationale: Boolean): Boolean =
        !preferences(context).getBoolean(KEY_OFFERED, false) &&
            permission(context) == Permission.NOT_ASKED &&
            !rationale

    fun markOffered(context: Context) {
        preferences(context).edit { putBoolean(KEY_OFFERED, true) }
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

    /** Keeps the key the server issued for [token] (`notification-token`'s `key`). */
    fun keepKey(context: Context, key: String, token: String) {
        if (key.isBlank() || token.isBlank()) return
        preferences(context).edit {
            putString(KEY_NOTIFICATION_KEY, key)
            putString(KEY_NOTIFICATION_KEY_TOKEN, token)
        }
    }

    /** The phone's notification key, while it's the current token's. */
    fun notificationKey(context: Context): String? {
        val preferences = preferences(context)
        val token = lastToken(context) ?: return null
        return preferences.getString(KEY_NOTIFICATION_KEY, null)
            ?.takeIf { preferences.getString(KEY_NOTIFICATION_KEY_TOKEN, null) == token }
    }

    /** Whether the page should register [token] now, to hand the app a key for it. */
    fun needsKey(context: Context, token: String?): Boolean =
        token != null && preferences(context).getString(KEY_NOTIFICATION_KEY_TOKEN, null) != token

    /**
     * Forgets this device's token when the session ends, so pushes for it
     * stop at Firebase too, and the key that fetched its words. The next
     * sign-in registers anew.
     */
    fun forget(context: Context) {
        val hadToken = lastToken(context) != null
        preferences(context).edit {
            remove(KEY_TOKEN)
            remove(KEY_OPTED_IN)
            remove(KEY_NOTIFICATION_KEY)
            remove(KEY_NOTIFICATION_KEY_TOKEN)
        }
        if (!hadToken || !isAvailable(context)) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        messaging.unregister()
    }

    /**
     * A push as FCM (or, in development builds, adb) hands it over: a Live
     * Update's step or end, or a notification, with its words fetched from
     * the server first when the push names them and the phone has a key.
     * Blocks while it fetches, at most [NotificationWords.TIMEOUT_MS] a try:
     * call it off the main thread.
     */
    fun receive(context: Context, data: Map<String, String>) {
        LiveProgress.from(data)?.let { return LiveUpdates.progress(context, it) }
        val push = Push.from(data) ?: return
        val words =
            push.notificationId?.let { id ->
                notificationKey(context)?.let { key -> NotificationWords.fetch(id, key) }
            }
        deliver(context, push, words)
    }

    /**
     * Shows a push as a notification, one per chat (its thread), a newer one
     * replacing it; a `resolved` push takes it away instead. Tapping it opens
     * the push's page in the app. With [words], it shows the chat's title,
     * what waits, and the answer's start or what's to decide, privately: the
     * lock screen of a phone that hides sensitive content shows the push's
     * own words instead.
     */
    fun deliver(context: Context, push: Push, words: NotificationWords.Text? = null) {
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

        val title = push.title ?: context.getString(R.string.app_name)
        val body = push.body ?: context.getString(push.defaultBody)
        val notification =
            builder(context, push, words?.title ?: title, words?.body ?: body)
                .setSubText(words?.subtitle)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(builder(context, push, title, body).build())
                .build()
        manager.notify(push.thread, NOTIFICATION_ID, notification)
    }

    private fun builder(context: Context, push: Push, title: String, body: String): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, if (push.waitsForPerson) CHANNEL_APPROVALS else CHANNEL_ANSWERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (push.waitsForPerson) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, push.url))

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

    /** One notification per chat: its thread is the tag, so a newer one (or the Live Update) takes its place. */
    const val NOTIFICATION_ID = 1

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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANSWERING,
                context.getString(R.string.channel_answering),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_answering_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANSWERS,
                context.getString(R.string.channel_answers),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_answers_description) }
        )
    }

    private fun askedBefore(context: Context) = preferences(context).getBoolean(KEY_ASKED, false)

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
