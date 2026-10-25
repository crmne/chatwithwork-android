package com.chatwithwork.app.sidebar

import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Fetches the sidebar's JSON as the signed-in person, with the web view's own
 * session: its cookies and its user agent, so the server sees the same app
 * the pages come from. No token of its own (docs/server-contract.md).
 */
object SidebarClient {
    sealed interface Result {
        data class Loaded(val sidebar: Sidebar) : Result

        /** 401: the session is gone. */
        data object SignedOut : Result

        /** Anything else (offline, an older server without the endpoint): keep what's shown. */
        data object Failed : Result
    }

    suspend fun fetch(url: String, userAgent: String?): Result = withContext(Dispatchers.IO) {
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (_: IOException) {
                return@withContext Result.Failed
            }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            userAgent?.let { connection.setRequestProperty("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.Loaded(Sidebar.parse(body))
                }

                HttpURLConnection.HTTP_UNAUTHORIZED -> Result.SignedOut

                else -> Result.Failed
            }
        } catch (_: IOException) {
            Result.Failed
        } catch (_: SerializationException) {
            Result.Failed
        } catch (_: IllegalArgumentException) {
            Result.Failed
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MS = 15_000
}
