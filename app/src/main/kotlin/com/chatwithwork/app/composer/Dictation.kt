package com.chatwithwork.app.composer

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.Build
import android.view.LayoutInflater
import android.webkit.CookieManager
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SnackbarHost
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dictation for the native composer: the phone records (OGG/Opus, at most
 * five minutes) and posts the recording to the page's dictation URL with the
 * web view's session, where it's transcribed in the EU like the web
 * composer's. Never Android's speech recognizer, which sends audio to Google.
 * See docs/server-contract.md, "The composer".
 */
object Dictation {
    /** OGG/Opus recording needs Android 10; earlier phones dictate in the page. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private const val MAX_MS = 5 * 60 * 1000
    private const val LEVEL_MS = 80L
    private const val MAX_AMPLITUDE = 32767

    @SuppressLint("InflateParams")
    fun start(
        activity: FragmentActivity,
        path: String,
        csrfToken: String?,
        userAgent: String?,
        onText: (String) -> Unit
    ) {
        if (!supported) return
        val file = File(activity.cacheDir, "dictation-${System.currentTimeMillis()}.ogg")
        val recorder =
            try {
                record(activity, file)
            } catch (_: Exception) {
                fail(activity, activity.getString(R.string.dictation_failed))
                return
            }

        val content = LayoutInflater.from(activity).inflate(R.layout.sheet_dictation, null)
        val status = content.findViewById<TextView>(R.id.dictation_status)
        val level = content.findViewById<LinearProgressIndicator>(R.id.dictation_level)
        val stop = content.findViewById<Button>(R.id.dictation_stop)
        val cancel = content.findViewById<Button>(R.id.dictation_cancel)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(content)
        dialog.setCancelable(false)

        var meter: Job? = null
        var stopped = false
        fun finish(send: Boolean) {
            if (stopped) return
            stopped = true
            meter?.cancel()
            runCatching { recorder.stop() }
            recorder.release()
            if (!send) {
                file.delete()
                dialog.dismiss()
                return
            }
            status.setText(R.string.dictation_transcribing)
            level.isIndeterminate = true
            stop.isEnabled = false
            activity.lifecycleScope.launch {
                val result = upload(AppConfig.baseUrl + path, file, csrfToken, userAgent)
                file.delete()
                dialog.dismiss()
                when {
                    result.text != null -> onText(result.text)
                    result.nothingHeard -> fail(activity, activity.getString(R.string.dictation_no_speech))
                    else -> fail(activity, result.error ?: activity.getString(R.string.dictation_failed))
                }
            }
        }

        recorder.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish(send = true)
        }
        stop.setOnClickListener { finish(send = true) }
        cancel.setOnClickListener { finish(send = false) }
        status.setText(R.string.dictation_listening)
        level.max = MAX_AMPLITUDE
        dialog.show()

        meter =
            activity.lifecycleScope.launch {
                while (isActive) {
                    level.setProgressCompat(runCatching { recorder.maxAmplitude }.getOrDefault(0), true)
                    delay(LEVEL_MS)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun record(activity: FragmentActivity, file: File): MediaRecorder {
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        recorder.setAudioSamplingRate(48_000)
        recorder.setAudioEncodingBitRate(32_000)
        recorder.setMaxDuration(MAX_MS)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        return recorder
    }

    private class Result(val text: String?, val error: String?, val nothingHeard: Boolean = false)

    private suspend fun upload(url: String, file: File, csrfToken: String?, userAgent: String?): Result =
        withContext(Dispatchers.IO) {
            val boundary = "dictation" + System.nanoTime()
            val connection =
                try {
                    URL(url).openConnection() as HttpURLConnection
                } catch (_: IOException) {
                    return@withContext Result(null, null)
                }
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                csrfToken?.let { connection.setRequestProperty("X-CSRF-Token", it) }
                userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                connection.outputStream.use { out ->
                    val disposition = "Content-Disposition: form-data; name=\"audio\"; filename=\"dictation.ogg\""
                    out.write("--$boundary\r\n$disposition\r\nContent-Type: audio/ogg\r\n\r\n".toByteArray())
                    file.inputStream().use { it.copyTo(out) }
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }
                val json = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                val text = json?.get("text")?.jsonPrimitive?.contentOrNull
                val error = json?.get("error")?.jsonPrimitive?.contentOrNull
                when {
                    code in 200..299 && !text.isNullOrBlank() -> Result(text, null)
                    code in 200..299 -> Result(null, null, nothingHeard = true)
                    else -> Result(null, error)
                }
            } catch (_: IOException) {
                Result(null, null)
            } finally {
                connection.disconnect()
            }
        }

    private fun fail(activity: FragmentActivity, message: String) {
        (activity as? SnackbarHost)?.showSnackbar(message, long = true)
    }
}
