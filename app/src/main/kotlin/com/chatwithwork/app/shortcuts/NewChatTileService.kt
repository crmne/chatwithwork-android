package com.chatwithwork.app.shortcuts

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService

/** The Quick Settings tile: New chat, opening the app on a new chat. */
class NewChatTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = ShortcutActivity.intent(this, dictate = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
