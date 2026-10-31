package com.chatwithwork.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android's delete intent for a working Live Update. It comes when the card
 * is cleared by the person or times out, never when the app takes it away on
 * return, which is what lets a person's dismissal hold for the run while the
 * app's own hiding doesn't ([LiveUpdates.dismissed]).
 */
class LiveUpdateDismissed : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val thread = intent.getStringExtra(EXTRA_THREAD) ?: return
        val run = intent.getStringExtra(EXTRA_RUN) ?: return
        LiveUpdates.dismissed(context, thread, run, intent.getLongExtra(EXTRA_POSTED_AT, 0))
    }

    companion object {
        const val EXTRA_THREAD = "thread"
        const val EXTRA_RUN = "run"
        const val EXTRA_POSTED_AT = "posted_at"
    }
}
