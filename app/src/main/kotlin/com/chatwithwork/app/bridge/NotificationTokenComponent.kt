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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * `notification-token`: lets a page turn on push notifications for this
 * phone and hand the token to the server, which it posts with the page's own
 * session and CSRF token. Same contract as the iOS app's component:
 *
 * - `connect` replies with the current state and never asks the person.
 *   Pages send it on load, so a token Firebase rotated reaches the server.
 * - `get` asks for the notification permission if Android hasn't asked yet
 *   (Android 13 and later), then replies. Send it from a tap.
 * - `openSettings` opens this app's notification settings, for someone who
 *   turned notifications off there.
 *
 * Replies to `connect` and `get`: `{status, token?, platform, environment,
 * appId}`. `status` is `authorized`, `denied`, or `not_determined`; `token`
 * is the FCM registration token, absent until authorized (and in builds
 * without a Firebase configuration); `platform` is "android";
 * `environment` is always "production" (FCM has no sandbox); `appId` is the
 * application id, which tells the server which app the token belongs to.
 */
class NotificationTokenComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val fragment get() = bridgeDelegate.destination.fragment

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> answer("connect")
            "get" -> requestThenAnswer()
            "openSettings" -> openSettings()
        }
    }

    private fun requestThenAnswer() {
        val context = fragment.context ?: return
        val host = fragment.activity as? NotificationPermissionHost

        if (PushNotifications.needsRuntimePermission(context) && host != null) {
            host.requestNotificationPermission { granted ->
                if (granted) PushNotifications.optIn(context)
                answer("get")
            }
        } else {
            // Allowed already (Android 12 and earlier, or given in Settings):
            // asking is the person turning push on.
            if (PushNotifications.permission(context) != PushNotifications.Permission.DENIED) {
                PushNotifications.optIn(context)
            }
            answer("get")
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
                    appId = context.packageName
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
    data class Reply(
        val status: String,
        val token: String? = null,
        val platform: String = "android",
        val environment: String = "production",
        val appId: String
    )

    companion object {
        val factory = BridgeComponentFactory("notification-token", ::NotificationTokenComponent)
    }
}

/** Implemented by the activity, which owns the permission prompt. */
interface NotificationPermissionHost {
    fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit)
}
