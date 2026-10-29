package com.chatwithwork.app.composer

import com.chatwithwork.app.composer.ComposerBar.Control
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which controls show in the composer's bar at a width, and which fold into "+". */
class ComposerBarTest {
    private val everything =
        ComposerState(
            dictation = ComposerState.Dictation("/482139075/dictations"),
            webSearch = ComposerState.WebSearch(on = false)
        )

    @Test
    fun `a phone's bar has room for the microphone and the globe, and + only attaches`() {
        // A 360dp phone: 12dp gutters and the box's 8dp padding leave 320dp.
        val bar = ComposerBar.inBar(320, everything, dictationSupported = true)
        assertThat(bar).containsExactly(Control.DICTATE, Control.WEB_SEARCH)
        val sheet = ComposerBar.inSheet(everything, bar, dictationSupported = true)
        assertThat(sheet.folded).isEmpty()
        assertThat(sheet.attachOnly).isTrue()
    }

    @Test
    fun `web search folds first, then dictation, as the bar narrows`() {
        assertThat(ComposerBar.inBar(279, everything, dictationSupported = true)).containsExactly(Control.DICTATE)
        assertThat(ComposerBar.inBar(236, everything, dictationSupported = true)).containsExactly(Control.DICTATE)
        assertThat(ComposerBar.inBar(235, everything, dictationSupported = true)).isEmpty()

        val sheet = ComposerBar.inSheet(everything, emptySet(), dictationSupported = true)
        assertThat(sheet.folded).containsExactly(Control.DICTATE, Control.WEB_SEARCH)
        assertThat(sheet.attachOnly).isFalse()
    }

    @Test
    fun `only what the page offers shows`() {
        val plain = ComposerState()
        assertThat(ComposerBar.inBar(400, plain, dictationSupported = true)).isEmpty()
        // Before the yes to models, or on Android 9, there's no microphone; the globe moves up into its place.
        val searchOnly = everything.copy(dictation = null)
        assertThat(ComposerBar.inBar(240, searchOnly, dictationSupported = true)).containsExactly(Control.WEB_SEARCH)
        assertThat(ComposerBar.inBar(320, everything, dictationSupported = false))
            .containsExactly(Control.WEB_SEARCH)
    }

    @Test
    fun `+ goes when it would hold nothing`() {
        val locked = everything.copy(acceptsFiles = false)
        val bar = ComposerBar.inBar(320, locked, dictationSupported = true)
        assertThat(ComposerBar.inSheet(locked, bar, dictationSupported = true).empty).isTrue()
        assertThat(ComposerBar.inSheet(locked, emptySet(), dictationSupported = true).empty).isFalse()
    }

    @Test
    fun `the model chip never takes Send's room`() {
        // A 360dp phone's 320dp bar with "+", the microphone and the globe: 320 - 3 * 44 - 8 - 44.
        assertThat(ComposerBar.modelMaxWidthDp(320, buttons = 3)).isEqualTo(136)
        // A wide bar lets it reach its widest, and it never drops below its narrowest.
        assertThat(ComposerBar.modelMaxWidthDp(600, buttons = 1)).isEqualTo(180)
        assertThat(ComposerBar.modelMaxWidthDp(0, buttons = 0)).isEqualTo(96)
    }
}
