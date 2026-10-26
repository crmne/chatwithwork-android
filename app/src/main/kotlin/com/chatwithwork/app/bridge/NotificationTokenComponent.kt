package com.chatwithwork.app.bridge

import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.push.PushNotifications
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * `notification-token`: lets a page turn on push notifications for this
 * phone and hand the token to the server, which it posts with the page's own
 * session and CSRF token. Same contract as the iOS app's component:
 *
 * - `connect` replies with the current state and never asks the person.
 *   Pages send it on load, so a token Firebase rotated reaches the server.
 *   When the person allows notifications (the offer, or Turn on), every
 *   component on screen answers its latest `connect` again
 *   with the new state ([answerConnected]): the page keeps the callback, so
 *   it posts the token without waiting for another page.
 * - `get` asks for the notification permission if Android hasn't asked yet
 *   (Android 13 and later), then replies. Send it from a tap.
 * - `offer` with `{reason}` (`approval` or `answer`): the page says now is a
 *   good moment (a change waits for approval, or an answer is taking a
 *   while). Once per install, while nobody has decided, the app explains
 *   why in its own words, then shows Android's prompt if the person goes on.
 *   Otherwise it replies at once.
 * - `openSettings` opens this app's notification settings, for someone who
 *   turned notifications off there.
 * - `key` with `{key, token}`: the phone's notification key, which the
 *   server answered the token's registration with. The app keeps it to fetch
 *   each notification's words from the server as its push arrives.
 *
 * Replies to `connect`, `get`, and `offer`: `{status, token?, platform, environment,
 * appId, needsKey}`. `status` is `authorized`, `denied`, or `not_determined`; `token`
 * is the FCM registration token, absent until authorized (and in builds
 * without a Firebase configuration); `platform` is "android";
 * `environment` is always "production" (FCM has no sandbox); `appId` is the
 * application id, which tells the server which app the token belongs to;
 * `needsKey` that the app has no key for the token yet, so the page
 * registers it now rather than once a day.
 */
class NotificationTokenComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val fragment get() = bridgeDelegate.destination.fragment

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                connected += this
                answer("connect")
            }

            "get" -> requestThenAnswer()

            "offer" -> offerThenAnswer(message.data<Offer>()?.reason)

            "openSettings" -> openSettings()

            "key" ->
                message.data<Key>()?.let { data ->
                    fragment.context?.let { PushNotifications.keepKey(it.applicationContext, data.key, data.token) }
                }
        }
    }

    private fun requestThenAnswer() {
        val context = fragment.context ?: return
        val host = fragment.activity as? NotificationPermissionHost

        if (PushNotifications.needsRuntimePermission(context) && host != null) {
            // The activity turns push on when the person allows it.
            host.requestNotificationPermission { answer("get") }
        } else {
            // Allowed already (Android 12 and earlier, or given in Settings):
            // asking is the person turning push on.
            if (PushNotifications.permission(context) != PushNotifications.Permission.DENIED) {
                PushNotifications.optIn(context)
            }
            answer("get")
        }
    }

    private fun offerThenAnswer(reason: String?) {
        val host = fragment.activity as? NotificationPermissionHost
        if (host == null) {
            answer("offer")
            return
        }
        host.offerNotifications(if (reason == "approval") OfferReason.APPROVAL else OfferReason.ANSWER) {
            answer("offer")
        }
    }

    private fun answer(event: String) {
        val context = fragment.context?.applicationContext ?: return
        val scope = fragment.viewLifecycleOwnerLiveData.value?.lifecycleScope ?: return
        scope.launch {
            val permission = PushNotifications.permission(context)
            val token =
                if (permission == PushNotifications.Permission.GRANTED) {
                    PushNotifications.token(context)
                } else {
                    null
                }
            replyTo(
                event,
                Reply(
                    status = permission.web,
                    token = token,
                    appId = context.packageName,
                    needsKey = PushNotifications.needsKey(context, token)
                )
            )
        }
    }

    private fun openSettings() {
        val context = fragment.context ?: return
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { fragment.startActivity(intent) }
    }

    @Serializable
    data class Offer(val reason: String? = null)

    @Serializable
    data class Key(val key: String = "", val token: String = "")

    @Serializable
    data class Reply(
        val status: String,
        val token: String? = null,
        val platform: String = "android",
        val environment: String = "production",
        val appId: String,
        val needsKey: Boolean = false
    )

    companion object {
        val factory = BridgeComponentFactory("notification-token", ::NotificationTokenComponent)

        /** Components that got a `connect`, for as long as their pages live. */
        private val connected = Collections.newSetFromMap(WeakHashMap<NotificationTokenComponent, Boolean>())

        /**
         * Answers every component's latest `connect` again, after the person
         * allowed notifications. Components whose page left the screen have
         * no bridge to answer through, and drop the reply.
         */
        fun answerConnected() {
            connected.toList().forEach { it.answer("connect") }
        }
    }
}

/** Why a page offers notifications, which picks the explanation's words. */
enum class OfferReason { APPROVAL, ANSWER }

/** Implemented by the activity, which owns the permission prompt. */
interface NotificationPermissionHost {
    fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit)

    /** Offers notifications once per install (see `offer`), then calls [onDone] either way. */
    fun offerNotifications(reason: OfferReason, onDone: () -> Unit)
}
