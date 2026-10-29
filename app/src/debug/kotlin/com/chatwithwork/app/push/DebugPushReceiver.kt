package com.chatwithwork.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

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
 * A Live Update's step is `--es kind progress --es phase answering --es
 * message "Searching Todoist" --es step search --es steps 2 --es sent_at <ms>`
 * with the same path and thread, and `--es notification_id <id>` fetches a
 * notification's words from the server, as FCM's would.
 *
 * Only the shell can send it (it holds DUMP, which apps can't get).
 */
class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val data = extras.keySet().mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
        if (data["kind"] == "live_preview") {
            val progress = LiveProgress.from(data + ("kind" to "progress")) ?: return
            PushNotifications.optIn(context)
            LiveUpdates.show(context, progress.url, "Northwind Studio")
            LiveUpdates.progress(context, progress.copy(sentAt = System.currentTimeMillis()))
            return
        }
        val pending = goAsync()
        thread(name = "debug-push") {
            try {
                PushNotifications.receive(context.applicationContext, data)
            } finally {
                pending.finish()
            }
        }
    }
}
