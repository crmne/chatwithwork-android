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
        /**
         * The microphone opened (true), or closed (false): stopped, full, failed,
         * or cancelled. False comes once, and only after true. The composer keeps
         * the screen on in between, or a phone that dims mid-sentence ends it.
         */
        fun onListening(listening: Boolean)

        /** The microphone's loudness, 0 to 1, about every 70 ms, and how long it's listened. */
        fun onLevel(level: Float, elapsedMs: Long)

        /** Recording ended; the words are on their way. */
        fun onTranscribing()

        /**
         * The transcription's words, or why there are none; never after [Session.cancel]. A [Result.retryable]
         * one leaves the session [Session.failed], holding the recording for [Session.retry] or [Session.cancel].
         */
        fun onResult(result: Result)
    }

    /**
     * What came back. [retryable] when the recording itself wasn't refused (the phone lost its connection, the
     * proxy in front of the app gave up, the model failed, credits ran out): sending it again can work, so it's kept.
     */
    class Result(
        val text: String?,
        val error: String?,
        val nothingHeard: Boolean = false,
        val retryable: Boolean = false
    )

    /** What the server refuses for the recording itself (malformed, too long, not audio): sending it again can't work. */
    internal val UNSENDABLE = setOf(400, 413, 415)

    /**
     * One dictation: records until [stop] (or five minutes), then posts the
     * recording to [path] with the web view's session and hands the words to
     * [listener]. [cancel] throws it away, recording or transcribing.
     * [microphone], [clock] and [post] are the phone's, or a test's.
     */
    class Session(
        context: Context,
        private val scope: CoroutineScope,
        private val path: String,
        private val csrfToken: String?,
        private val userAgent: String?,
        private val listener: Listener,
        private val microphone: (file: File, onEnd: () -> Unit) -> Microphone = { file, onEnd ->
            PhoneMicrophone(context, file, onEnd)
        },
        private val clock: () -> Long = SystemClock::elapsedRealtime,
        private val post: (suspend (File, AtomicReference<HttpURLConnection?>) -> Result)? = null
    ) {
        private val file = File(context.cacheDir, "dictation-${System.currentTimeMillis()}.ogg")
        private var recorder: Microphone? = null
        private var job: Job? = null

        // The transcription's request while it's on its way, so Cancel can drop it.
        private val request = AtomicReference<HttpURLConnection?>()
        private var started = 0L
        private val speech = SpeechActivity()
        var transcribing = false
            private set
        var finished = false
            private set

        /** Transcribing failed, and the recording waits for [retry] or [cancel]. */
        var failed = false
            private set

        /** The recording failed once and is kept until it's transcribed or [cancel]led, through a [retry] too. */
        var kept = false
            private set

        /** The microphone is open. */
        val listening: Boolean get() = recorder != null

        /** Starts recording; false when the microphone couldn't. */
        fun start(): Boolean {
            val recorder =
                try {
                    // Five minutes, or the recorder failing, end it as Stop does.
                    microphone(file) { stop() }
                } catch (_: Exception) {
                    file.delete()
                    return false
                }
            this.recorder = recorder
            started = clock()
            listener.onListening(true)
            job =
                scope.launch {
                    while (isActive) {
                        val amplitude = runCatching { recorder.maxAmplitude }.getOrNull()
                        val elapsed = clock() - started
                        if (amplitude != null) speech.observe(amplitude / MAX_AMPLITUDE, elapsed)
                        listener.onLevel(loudness(amplitude ?: 0), elapsed)
                        delay(LEVEL_MS)
                    }
                }
            return true
        }

        /** Stops recording and transcribes what was heard. */
        fun stop() {
            if (finished || transcribing || failed) return
            transcribing = true
            job?.cancel()
            release()
            if (clock() - started < 500 || !speech.canTranscribe) {
                finished = true
                file.delete()
                listener.onResult(Result(null, null, nothingHeard = true))
                return
            }
            send()
        }

        /** Sends a recording that couldn't be transcribed again. */
        fun retry() {
            if (finished || !failed) return
            failed = false
            transcribing = true
            send()
        }

        private fun send() {
            listener.onTranscribing()
            job =
                scope.launch {
                    val result =
                        post?.invoke(file, request)
                            ?: upload(AppConfig.baseUrl + path, file, csrfToken, userAgent, request)
                    if (finished) return@launch
                    transcribing = false
                    if (result.retryable) {
                        // Kept on the phone, so nothing said is lost.
                        failed = true
                        kept = true
                    } else {
                        file.delete()
                        finished = true
                    }
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
            recorder.close()
            listener.onListening(false)
        }
    }

    /** What a [Session] records with. */
    interface Microphone {
        /** The loudest moment since it was last asked, 0 to 32767. */
        val maxAmplitude: Int

        /** Stops recording and lets the microphone go. */
        fun close()
    }

    /**
     * The phone's microphone, recording OGG/Opus into [file] until [close], or
     * until five minutes or a failure, when it calls [onEnd]. Throws when it
     * can't start, as it can't before Android 10.
     */
    private class PhoneMicrophone(context: Context, file: File, onEnd: () -> Unit) : Microphone {
        private val recorder: MediaRecorder

        init {
            if (!supported) throw IOException("Recording OGG/Opus needs Android 10")
            recorder = record(context, file)
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onEnd()
            }
            recorder.setOnErrorListener { _, _, _ -> onEnd() }
        }

        override val maxAmplitude: Int get() = recorder.maxAmplitude

        override fun close() {
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
                return@withContext Result(null, null, retryable = true)
            }
        active.set(connection)
        try {
            // Cancelled before the request was published here, so Cancel couldn't drop it.
            if (!isActive) return@withContext Result(null, null, retryable = true)
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
                // Opening the stream connects: a Cancel meanwhile sends nothing.
                if (!isActive) return@withContext Result(null, null, retryable = true)
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
            result(code, text, error)
        } catch (_: IOException) {
            Result(null, null, retryable = true)
        } finally {
            active.compareAndSet(connection, null)
            connection.disconnect()
        }
    }
}

/**
 * What a response says: words, none heard, or why not. Anything but the app's own answer (the proxy's 504 page after
 * 30 seconds, a lost connection) has no [Result.error] of its own, and the recording is kept to send again.
 */
internal fun result(code: Int, text: String?, error: String?): Dictation.Result = when {
    code in 200..299 && text != null && text.isNotBlank() -> Dictation.Result(text, null)

    code in 200..299 && text != null -> Dictation.Result(null, null, nothingHeard = true)

    // Only the app's own refusal, with its words: a proxy's page with the same status keeps the recording.
    code in Dictation.UNSENDABLE && error != null -> Dictation.Result(null, error)

    else -> Dictation.Result(null, error, retryable = true)
}

/** Measured silence and brief clicks stay on the phone; absent metering fails open. */
internal class SpeechActivity {
    private var started: Long? = null
    private var measured = false
    private var heard = false
    val canTranscribe: Boolean get() = !measured || heard

    fun observe(level: Double, elapsedMs: Long) {
        measured = true
        if (level > 0.02) {
            if (started == null) started = elapsedMs
            if (elapsedMs - (started ?: elapsedMs) >= 300) heard = true
        } else {
            started = null
        }
    }
}
