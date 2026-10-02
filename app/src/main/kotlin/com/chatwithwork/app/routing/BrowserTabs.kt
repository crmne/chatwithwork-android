package com.chatwithwork.app.routing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R
import com.google.android.material.snackbar.Snackbar

/**
 * Opens links in a Chrome Custom Tab: the browser the person already uses,
 * shown over the app in its colors, with their own cookies and passwords.
 * Sign-in pages of other services (Google, Microsoft, Slack) refuse to run
 * inside an app's web view, so every flow that leaves the site goes here.
 */
object BrowserTabs {
    /** Opens [uri] in a browser tab. False when nothing on the phone can open it. */
    fun open(activity: Activity, uri: Uri, ephemeral: Boolean = false): Boolean {
        val intent =
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .setShareState(if (ephemeral) CustomTabsIntent.SHARE_STATE_OFF else CustomTabsIntent.SHARE_STATE_ON)
                .setUrlBarHidingEnabled(true)
                .setColorSchemeParams(
                    CustomTabsIntent.COLOR_SCHEME_LIGHT,
                    colors(activity, R.color.lw_canvas_light_mode)
                )
                .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colors(activity, R.color.lw_canvas_dark_mode))
                .setEphemeralBrowsingEnabled(ephemeral)
                .build()

        return try {
            intent.launchUrl(activity, uri)
            true
        } catch (_: ActivityNotFoundException) {
            openWithSystem(activity, uri)
        }
    }

    /** Hands [uri] to whichever app handles it (mail, phone, maps, another browser). */
    fun openWithSystem(activity: Activity, uri: Uri): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            Snackbar.make(it, R.string.external_link_failed, Snackbar.LENGTH_LONG).show()
        }
        false
    } catch (_: SecurityException) {
        // A target that won't take our intent; there's nothing to fall back to.
        false
    }

    private fun colors(activity: Activity, colorRes: Int): CustomTabColorSchemeParams {
        val color = ContextCompat.getColor(activity, colorRes)
        return CustomTabColorSchemeParams
            .Builder()
            .setToolbarColor(color)
            .setNavigationBarColor(color)
            .build()
    }
}
