package com.chatwithwork.app.shortcuts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.main.LaunchLinks
import com.chatwithwork.app.main.MainActivity

/**
 * Where the home screen widget, the Quick Settings tile, and the icon's
 * shortcuts (xml/shortcuts.xml) lead: it opens a new chat, New bot, or
 * Decisions, and for Dictate also asks the next composer that connects to
 * start dictating. It isn't exported, so only the app's own widget and tile
 * (through their PendingIntents) and the launcher's shortcuts reach it, and
 * the dictation request stays inside the process.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_DICTATE, false) == true) PendingShares.askDictation()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(
                    when (intent?.action) {
                        LaunchLinks.ACTION_NEW_BOT -> LaunchLinks.ACTION_NEW_BOT
                        LaunchLinks.ACTION_DECISIONS -> LaunchLinks.ACTION_DECISIONS
                        else -> LaunchLinks.ACTION_NEW_CHAT
                    }
                )
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
