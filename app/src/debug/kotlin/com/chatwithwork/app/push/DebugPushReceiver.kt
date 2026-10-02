package com.chatwithwork.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Development builds only: shows a push as if Firebase had delivered it, so
 * notifications can be tried without a Firebase project. The extras are the
 * push's data keys (docs/push-notifications.md):
 *
 * ```
 * adb shell am broadcast -n com.chatwithwork.app.debug/com.chatwithwork.app.push.DebugPushReceiver \
 *   --es kind approval_waiting --es path /482139075/chats/42 --es title Acme \
 *   --es body "A change is waiting for your approval in Slack."
 * ```
 *
 * Only the shell can send it (it holds DUMP, which apps can't get).
 */
class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val data = extras.keySet().mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
        Push.from(data)?.let { PushNotifications.deliver(context, it) }
    }
}
