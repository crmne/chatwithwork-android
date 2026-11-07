package com.chatwithwork.app.push

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit

/**
 * The one tip about a phone maker's status bar capsule. OnePlus's OxygenOS 16
 * and OPPO's ColorOS 16 show Android 16's Live Updates in their Live Alerts
 * capsule only for apps the person turns it on for ("Show Live Updates on Live
 * Alerts", in the app's notification settings), off by default. Stock Android
 * shows the chip without asking, and Samsung's switch for the Now Bar is in
 * its lock screen settings, which no intent opens, so neither gets the tip.
 *
 * Offered once per install, kept when someone signs out like the
 * notifications offer.
 */
object LiveAlertsTip {
    private const val PREFERENCES = "live_alerts_tip"
    private const val KEY_OFFERED = "offered"

    /** Phone makers whose Live Alerts need the per-app switch. */
    private val MAKERS = setOf("oneplus", "oppo")

    /** Whether this phone's system has the switch the tip is about. */
    fun applies(manufacturer: String = Build.MANUFACTURER, sdk: Int = Build.VERSION.SDK_INT): Boolean =
        sdk >= Build.VERSION_CODES.BAKLAVA && manufacturer.trim().lowercase() in MAKERS

    fun shouldOffer(context: Context): Boolean = applies() && !preferences(context).getBoolean(KEY_OFFERED, false)

    fun markOffered(context: Context) = preferences(context).edit { putBoolean(KEY_OFFERED, true) }

    /** The app's notification settings, where the switch is; the app's info page if a skin has none. */
    fun openSettings(context: Context) {
        val notifications =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            context.startActivity(notifications)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
