package com.chatwithwork.app

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * Where this build points and how it introduces itself to the server.
 */
object AppConfig {
    /** The site this build wraps, without a trailing slash. Set per build type. */
    val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/')

    /** "production", "staging", or "development". */
    val environment: String = BuildConfig.ENVIRONMENT

    /**
     * The path configuration the server serves for this app. The version is in
     * the name: a change the installed apps can't read gets a new file
     * (android_v2.json) while old installs keep reading v1.
     */
    val remotePathConfigurationUrl: String = "$baseUrl/configurations/android_v1.json"

    const val BUNDLED_PATH_CONFIGURATION = "json/path-configuration.json"

    /**
     * The user agent prefix the server reads. Hotwire Native appends its own
     * tokens ("Hotwire Native Android; Turbo Native Android; bridge-components:
     * [...];") and the WebView's user agent after it. See docs/server-contract.md.
     */
    fun userAgentPrefix(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return userAgentPrefix(
            versionName = info.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(info)
        )
    }

    fun userAgentPrefix(versionName: String, versionCode: Long): String =
        "Chat with Work; platform=android; version=$versionName; build=$versionCode;"
}
