package com.chatwithwork.app.files

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a file of the server's as the signed-in person, with the web
 * view's own cookies and user agent, into a folder of its own in the cache
 * (`shared/`, which only the FileProvider hands out), keeping its name, so the
 * share sheet and the app it goes to see the file by its name.
 */
object FileDownloads {
    private const val FOLDER = "shared"
    private const val TIMEOUT_MS = 30_000

    /** Downloads [url] as [name]; null when it didn't download. */
    suspend fun fetch(context: Context, url: String, name: String, userAgent: String?): File? =
        withContext(Dispatchers.IO) {
            forgetOld(context)
            val folder = File(File(context.cacheDir, FOLDER), UUID.randomUUID().toString())
            val file = File(folder, safeName(name))
            val connection =
                try {
                    URL(url).openConnection() as HttpURLConnection
                } catch (_: IOException) {
                    return@withContext null
                }
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

                folder.mkdirs()
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                file
            } catch (_: IOException) {
                folder.deleteRecursively()
                null
            } finally {
                connection.disconnect()
            }
        }

    /** A file name with no folders in it, never empty. */
    fun safeName(name: String): String {
        val clean = name.replace('/', '-').replace('\\', '-').trim()
        return if (clean.isEmpty() || clean == "." || clean == "..") "file" else clean
    }

    /** Files shared a day ago or more: the apps they went to have read them by now. */
    private fun forgetOld(context: Context) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        File(context.cacheDir, FOLDER).listFiles()?.filter {
            it.lastModified() < cutoff
        }?.forEach { it.deleteRecursively() }
    }
}
