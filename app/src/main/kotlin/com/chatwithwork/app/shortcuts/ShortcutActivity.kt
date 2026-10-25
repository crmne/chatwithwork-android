package com.chatwithwork.app.shortcuts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.main.LaunchLinks
import com.chatwithwork.app.main.MainActivity

/**
 * Where the home screen widget and the Quick Settings tile lead: it opens a
 * new chat, and for Dictate also asks the next composer that connects to
 * start dictating. It isn't exported, so only the app's own widget and tile
 * (through their PendingIntents) reach it, and the dictation request stays
 * inside the process.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_DICTATE, false) == true) PendingShares.askDictation()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(LaunchLinks.ACTION_NEW_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        private const val EXTRA_DICTATE = "dictate"

        fun intent(context: Context, dictate: Boolean): Intent = Intent(context, ShortcutActivity::class.java)
            .putExtra(EXTRA_DICTATE, dictate)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
