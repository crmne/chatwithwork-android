package com.chatwithwork.app

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * Where this build points and how it introduces itself to the server.
 */
object AppConfig {
    /**
     * Whether the app draws its own composer (the `composer` bridge component,
     * ComposerView). Off, `composer` isn't registered, so it's missing from the
     * user agent's `bridge-components` and the page shows its own web composer
     * (native.css hides it only where the list names `composer`). The native
     * composer's code stays; turning this back on brings it back.
     */
    const val NATIVE_COMPOSER = false

    /** The site this build wraps, without a trailing slash. Set per build type. */
    val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/')

    /** "production", "staging", or "development". */
    val environment: String = BuildConfig.ENVIRONMENT

    /**
     * The path configuration the server serves for this app. The version is in
     * the name: a change the installed apps can't read gets a new file while
     * old installs keep reading theirs. v3: every place (a chat, Decisions,
     * Bots, Settings) replaces the one root screen, and New chat is a modal
     * over it, like New bot. v4: New chat and New bot's sentence are pages
     * again, which turn into their chat in place as the question goes
     * (NewChatHandOff): pushed over the page they're opened from, or the root
     * when the sidebar or a shortcut opens them. Builds 1.2.11 and 1.2.12
     * read v3; the sidebar builds before them v2, where New chat is a place
     * (they start the navigator on it, which a modal can't be); the tab
     * builds v1.
     */
    val remotePathConfigurationUrl: String = "$baseUrl/configurations/android_v4.json"

    const val BUNDLED_PATH_CONFIGURATION = "json/path-configuration.json"

    /**
     * The user agent prefix the server reads. Hotwire Native appends its own
     * tokens ("Hotwire Native Android; Turbo Native Android; bridge-components:
     * [...];") and the WebView's user agent after it. `new-chat=page` says New
     * chat and New bot are pages here (v4), so the server draws what they
     * turn into in place. See docs/server-contract.md.
     */
    fun userAgentPrefix(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return userAgentPrefix(
            versionName = info.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(info)
        )
    }

    fun userAgentPrefix(versionName: String, versionCode: Long): String =
        "Chat with Work; platform=android; version=$versionName; build=$versionCode; new-chat=page;"
}
