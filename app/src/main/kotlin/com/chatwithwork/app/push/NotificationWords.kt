package com.chatwithwork.app.push

import com.chatwithwork.app.AppConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a notification says, fetched from the server as the push arrives, so
 * the chat's words never pass through Google's push service
 * (docs/server-contract.md, "Push notifications"). The push carries
 * `notification_id`, an id the app can't read; the app asks the server for
 * its words with the phone's notification key, which the page handed it
 * when it registered the token (`notification-token`'s `key`), and shows the
 * push's own words if that fails or takes too long.
 */
object NotificationWords {
    /** `GET /native/notifications/:id`'s answer. */
    @Serializable
    data class Text(val title: String = "", val subtitle: String? = null, val body: String = "")

    /** How long a push waits for its words before it shows its own. */
    const val TIMEOUT_MS = 5_000

    private val ID = Regex("[A-Za-z0-9_-]+")
    private val json = Json { ignoreUnknownKeys = true }

    /** The words' address on this build's server, for an id shaped like the server's; never another path. */
    fun url(id: String): String? = if (ID.matches(id)) "${AppConfig.baseUrl}/native/notifications/$id" else null

    fun parse(body: String): Text? = runCatching { json.decodeFromString<Text>(body) }
        .getOrNull()
        ?.takeIf { it.title.isNotBlank() && it.body.isNotBlank() }

    /** The words for the notification [id] names, or null. Blocks: call it off the main thread. */
    fun fetch(id: String, key: String): Text? {
        val url = url(id) ?: return null
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (_: IOException) {
                return null
            }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parse(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
