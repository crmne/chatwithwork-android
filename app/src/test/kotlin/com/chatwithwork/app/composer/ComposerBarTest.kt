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
    fun `a 408dp phone has room for the microphone and the globe, and + only attaches`() {
        // The screen less 16dp gutters and the box's 8dp padding, where the web's own bar reaches 24rem.
        val bar = ComposerBar.inBar(360, everything, dictationSupported = true)
        assertThat(bar).containsExactly(Control.DICTATE, Control.WEB_SEARCH)
        val sheet = ComposerBar.inSheet(everything, bar, dictationSupported = true)
        assertThat(sheet.folded).isEmpty()
        assertThat(sheet.attachOnly).isTrue()
    }

    @Test
    fun `web search folds first, then dictation, as the bar narrows`() {
        // Web search folds on the phones the web's does (a 390dp one here), into "+" as a switch.
        val narrow = ComposerBar.inBar(342, everything, dictationSupported = true)
        assertThat(narrow).containsExactly(Control.DICTATE)
        assertThat(ComposerBar.inSheet(everything, narrow, dictationSupported = true).folded)
            .containsExactly(Control.WEB_SEARCH)
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
        // Before the yes to models, or on Android 9, there's no microphone; the globe keeps its own width.
        val searchOnly = everything.copy(dictation = null)
        assertThat(ComposerBar.inBar(360, searchOnly, dictationSupported = true)).containsExactly(Control.WEB_SEARCH)
        assertThat(ComposerBar.inBar(359, searchOnly, dictationSupported = true)).isEmpty()
        assertThat(ComposerBar.inBar(360, everything, dictationSupported = false))
            .containsExactly(Control.WEB_SEARCH)
    }

    @Test
    fun `+ goes when it would hold nothing`() {
        val locked = everything.copy(acceptsFiles = false)
        val bar = ComposerBar.inBar(360, locked, dictationSupported = true)
        assertThat(ComposerBar.inSheet(locked, bar, dictationSupported = true).empty).isTrue()
        assertThat(ComposerBar.inSheet(locked, emptySet(), dictationSupported = true).empty).isFalse()
    }

    @Test
    fun `permissions keep the plus menu available without file attachments`() {
        val state = ComposerState(
            acceptsFiles = false,
            permissionModes = ComposerState.PermissionModes(
                "ask",
                listOf(ComposerState.PermissionOption("ask", "Ask for changes"))
            )
        )
        val sheet = ComposerBar.inSheet(state, emptySet())
        assertThat(sheet.empty).isFalse()
        assertThat(sheet.attachOnly).isFalse()
        assertThat(sheet.permissions).isTrue()
        assertThat(ComposerBar.inSheet(state.copy(acceptsFiles = true), emptySet()).attachOnly).isFalse()
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
