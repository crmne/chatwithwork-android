package com.chatwithwork.app.composer

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import com.chatwithwork.app.AppConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log10
import kotlinx.coroutines.CoroutineScope
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
 * While it listens and transcribes, the composer draws it in its field's
 * place (ComposerView). See docs/server-contract.md, "The composer".
 */
object Dictation {
    /** OGG/Opus recording needs Android 10; earlier phones dictate in the page. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    const val MAX_MS = 5 * 60 * 1000
    private const val LEVEL_MS = 70L
    private const val MAX_AMPLITUDE = 32767.0

    // The level, from a whisper to shouting, as the waveform draws it (the web's dictation_controller.js maps
    // -50 to -10 dB of RMS; MediaRecorder gives the peak, which runs a little louder).
    private const val QUIET_DB = -50.0
    private const val LOUD_DB = -6.0

    /** What the composer draws while a [Session] runs. */
    interface Listener {
        /** The microphone's loudness, 0 to 1, about every 70 ms, and how long it's listened. */
        fun onLevel(level: Float, elapsedMs: Long)

        /** Recording ended; the words are on their way. */
        fun onTranscribing()

        /** The transcription's words, or why there are none; never after [Session.cancel]. */
        fun onResult(result: Result)
    }

    class Result(val text: String?, val error: String?, val nothingHeard: Boolean = false)

    /**
     * One dictation: records until [stop] (or five minutes), then posts the
     * recording to [path] with the web view's session and hands the words to
     * [listener]. [cancel] throws it away, recording or transcribing.
     */
    class Session(
        private val context: Context,
        private val scope: CoroutineScope,
        private val path: String,
        private val csrfToken: String?,
        private val userAgent: String?,
        private val listener: Listener
    ) {
        private val file = File(context.cacheDir, "dictation-${System.currentTimeMillis()}.ogg")
        private var recorder: MediaRecorder? = null
        private var job: Job? = null

        // The transcription's request while it's on its way, so Cancel can drop it.
        private val request = AtomicReference<HttpURLConnection?>()
        private var started = 0L
        var transcribing = false
            private set
        var finished = false
            private set

        /** Starts recording; false when the microphone couldn't. */
        fun start(): Boolean {
            if (!supported) return false
            val recorder =
                try {
                    record(context, file)
                } catch (_: Exception) {
                    file.delete()
                    return false
                }
            this.recorder = recorder
            started = SystemClock.elapsedRealtime()
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop()
            }
            job =
                scope.launch {
                    while (isActive) {
                        val amplitude = runCatching { recorder.maxAmplitude }.getOrDefault(0)
                        listener.onLevel(loudness(amplitude), SystemClock.elapsedRealtime() - started)
                        delay(LEVEL_MS)
                    }
                }
            return true
        }

        /** Stops recording and transcribes what was heard. */
        fun stop() {
            if (finished || transcribing) return
            transcribing = true
            job?.cancel()
            release()
            listener.onTranscribing()
            job =
                scope.launch {
                    val result = upload(AppConfig.baseUrl + path, file, csrfToken, userAgent, request)
                    file.delete()
                    if (finished) return@launch
                    finished = true
                    listener.onResult(result)
                }
        }

        /** Throws the recording away, or ignores the transcription still on its way. */
        fun cancel() {
            if (finished) return
            finished = true
            job?.cancel()
            // A blocking request ignores the coroutine's cancelling: closing it stops the upload.
            request.getAndSet(null)?.disconnect()
            release()
            file.delete()
        }

        private fun release() {
            val recorder = recorder ?: return
            this.recorder = null
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    /** A peak amplitude (0 to 32767) as the waveform's 0 to 1. */
    fun loudness(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val decibels = 20 * log10(amplitude / MAX_AMPLITUDE)
        return ((decibels - QUIET_DB) / (LOUD_DB - QUIET_DB)).coerceIn(0.0, 1.0).toFloat()
    }

    /** How long it's listened, as "0:07" or "1:42", as the web composer writes it. */
    fun elapsed(ms: Long): String {
        val seconds = ms / 1000
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun record(context: Context, file: File): MediaRecorder {
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
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

    private suspend fun upload(
        url: String,
        file: File,
        csrfToken: String?,
        userAgent: String?,
        active: AtomicReference<HttpURLConnection?>
    ): Result = withContext(Dispatchers.IO) {
        val boundary = "dictation" + System.nanoTime()
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (_: IOException) {
                return@withContext Result(null, null)
            }
        active.set(connection)
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
            active.compareAndSet(connection, null)
            connection.disconnect()
        }
    }
}
