package com.chatwithwork.app.composer

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test

/**
 * When a dictation holds the screen on: from the microphone opening until it
 * closes, whichever way it closes (ComposerView keeps the screen on in between).
 */
class DictationSessionTest {
    private val dir = createTempDirectory("dictation").toFile()
    private val context =
        object : ContextWrapper(null) {
            override fun getCacheDir(): File = dir
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val heard = mutableListOf<String>()
    private val listener =
        object : Dictation.Listener {
            override fun onListening(listening: Boolean) {
                heard += if (listening) "listening" else "done listening"
            }

            override fun onLevel(level: Float, elapsedMs: Long) = Unit

            override fun onTranscribing() {
                heard += "transcribing"
            }

            override fun onResult(result: Dictation.Result) {
                heard += if (result.retryable) "failed" else result.text ?: "result"
            }
        }

    private var closed = 0
    private var end: (() -> Unit)? = null

    private fun session(opens: Boolean = true) = Dictation.Session(
        context,
        scope,
        "/dictations",
        null,
        null,
        listener,
        microphone = { _, onEnd ->
            if (!opens) throw IOException("busy")
            end = onEnd
            object : Dictation.Microphone {
                override val maxAmplitude = 0

                override fun close() {
                    closed++
                }
            }
        },
        // Too short to transcribe, so stopping never posts anything.
        clock = { 0L }
    )

    private var now = 0L
    private val answers = ArrayDeque<Dictation.Result>()
    private val sent = mutableListOf<Boolean>()

    // A microphone that can't be metered, so what it records is sent, and a server that answers from [answers].
    private fun sending() = Dictation.Session(
        context,
        scope,
        "/dictations",
        null,
        null,
        listener,
        microphone = { file, _ ->
            file.writeBytes(byteArrayOf(1, 2, 3))
            object : Dictation.Microphone {
                override val maxAmplitude: Int get() = throw IllegalStateException("not metered")

                override fun close() = Unit
            }
        },
        clock = { now },
        post = { file, _ ->
            sent += file.exists()
            answers.removeFirst()
        }
    )

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `the screen stays on from the microphone opening until Stop closes it`() {
        val dictation = session()
        assertThat(dictation.start()).isTrue()
        assertThat(heard).containsExactly("listening")

        dictation.stop()
        assertThat(heard).containsExactly("listening", "done listening", "result").inOrder()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `Cancel, leaving the screen, or locking the phone lets the screen go`() {
        val dictation = session()
        dictation.start()
        dictation.cancel()
        assertThat(heard).containsExactly("listening", "done listening").inOrder()

        dictation.cancel()
        dictation.stop()
        assertThat(heard).containsExactly("listening", "done listening").inOrder()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `five minutes or the recorder failing lets the screen go`() {
        val dictation = session()
        dictation.start()
        end!!.invoke()
        assertThat(heard).containsExactly("listening", "done listening", "result").inOrder()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `a microphone that will not open never holds the screen`() {
        assertThat(session(opens = false).start()).isFalse()
        assertThat(heard).isEmpty()
    }

    // On November 7, 2026 the proxy in front of the app answered three dictations with a 504 of its own after 30
    // seconds, and each recording was lost.
    @Test
    fun `a recording that couldn't be transcribed is kept until it's sent again`() {
        answers += result(504, null, null)
        answers += Dictation.Result("Find the Q3 budget.", null)
        val dictation = sending()
        dictation.start()
        now = 3_000
        dictation.stop()

        assertThat(heard).containsExactly("listening", "done listening", "transcribing", "failed").inOrder()
        assertThat(dictation.failed).isTrue()
        assertThat(dictation.finished).isFalse()
        dictation.stop()
        assertThat(sent).hasSize(1)

        dictation.retry()
        assertThat(sent).containsExactly(true, true).inOrder()
        assertThat(heard.last()).isEqualTo("Find the Q3 budget.")
        assertThat(dictation.finished).isTrue()
        assertThat(dir.listFiles()).isEmpty()
    }

    @Test
    fun `Discard throws a recording that couldn't be transcribed away`() {
        answers += result(502, null, "Couldn't transcribe that. Try again in a moment.")
        val dictation = sending()
        dictation.start()
        now = 3_000
        dictation.stop()
        assertThat(dir.listFiles()).hasLength(1)

        dictation.cancel()
        dictation.retry()
        assertThat(sent).hasSize(1)
        assertThat(dir.listFiles()).isEmpty()
    }

    @Test
    fun `a recording the server refuses for itself is not kept`() {
        answers += result(415, null, "That recording is in a format we can't transcribe.")
        val dictation = sending()
        dictation.start()
        now = 3_000
        dictation.stop()

        assertThat(heard.last()).isEqualTo("result")
        assertThat(dictation.finished).isTrue()
        assertThat(dir.listFiles()).isEmpty()
    }
}
