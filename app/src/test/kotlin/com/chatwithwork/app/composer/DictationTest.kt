package com.chatwithwork.app.composer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** What the dictation line draws: the waveform's level and the time beside it. */
class DictationTest {
    @Test
    fun `the level runs from a whisper to shouting, clamped to 0 and 1`() {
        assertThat(Dictation.loudness(0)).isEqualTo(0f)
        assertThat(Dictation.loudness(-5)).isEqualTo(0f)
        // -50 dB and quieter is silence; -6 dB and louder is the top.
        assertThat(Dictation.loudness(100)).isEqualTo(0f)
        assertThat(Dictation.loudness(32767)).isEqualTo(1f)
        assertThat(Dictation.loudness(20000)).isEqualTo(1f)
        val speech = Dictation.loudness(2000)
        assertThat(speech).isGreaterThan(0.3f)
        assertThat(speech).isLessThan(0.9f)
        assertThat(Dictation.loudness(4000)).isGreaterThan(speech)
    }

    @Test
    fun `the time reads as minutes and seconds, as the web composer writes it`() {
        assertThat(Dictation.elapsed(0)).isEqualTo("0:00")
        assertThat(Dictation.elapsed(7_900)).isEqualTo("0:07")
        assertThat(Dictation.elapsed(102_000)).isEqualTo("1:42")
        assertThat(Dictation.elapsed(Dictation.MAX_MS.toLong())).isEqualTo("5:00")
    }
}
