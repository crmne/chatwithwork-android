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
                heard += "result"
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
}
