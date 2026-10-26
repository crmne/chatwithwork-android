package com.chatwithwork.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Chat with Work's pushes. They are data-only messages that never
 * carry chat content (docs/push-notifications.md); the app fetches the
 * chat's words from the server and writes the notification itself, so a tap
 * opens the right chat, a resolved change can take its notification away,
 * and a Live Update shows the answer's steps. FCM calls this off the main
 * thread and waits for it.
 */
class PushMessagingService : FirebaseMessagingService() {
    @Deprecated("FCM 25.1 hands tokens to onRegistered; kept for tokens rotated outside a registration.")
    override fun onNewToken(token: String) {
        PushNotifications.remember(this, token)
    }

    override fun onRegistered(token: String) {
        // The page registers the token with the server the next time it asks
        // (`notification-token` replies with the token it has).
        PushNotifications.remember(this, token)
    }

    override fun onUnregistered(token: String) {
        if (PushNotifications.lastToken(this) == token) PushNotifications.forget(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.receive(this, message.data)
    }
}
