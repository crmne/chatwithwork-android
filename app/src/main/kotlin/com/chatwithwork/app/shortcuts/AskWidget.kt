package com.chatwithwork.app.shortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.chatwithwork.app.R

/**
 * The home screen widget: "Ask anything" opens a new chat, the microphone
 * opens one dictating. It shows nothing from the person's chats, so it never
 * needs updating.
 */
class AskWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_ask)
        views.setOnClickPendingIntent(R.id.widget_ask, pending(context, dictate = false))
        views.setOnClickPendingIntent(R.id.widget_dictate, pending(context, dictate = true))
        manager.updateAppWidget(ids, views)
    }

    private fun pending(context: Context, dictate: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (dictate) 1 else 0,
        ShortcutActivity.intent(context, dictate),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
