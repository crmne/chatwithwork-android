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
    fun `a 408dp phone has room for the microphone and the globe, and nothing folds`() {
        // The screen less 16dp gutters and the box's 8dp padding, where the web's own bar reaches 24rem.
        val bar = ComposerBar.inBar(360, everything, dictationSupported = true)
        assertThat(bar).containsExactly(Control.DICTATE, Control.WEB_SEARCH)
        val sheet = ComposerBar.inSheet(everything, bar, dictationSupported = true)
        assertThat(sheet.folded).isEmpty()
        assertThat(sheet.attach).isTrue()
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
    fun `+ knows when it holds nothing, which disables it`() {
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
        assertThat(sheet.permissions).isTrue()
    }

    @Test
    fun `the model chip never takes Send's room`() {
        // A 360dp phone's 320dp bar with "+", the microphone and the globe: 320 - 3 * 44 - 8 - 44.
        assertThat(ComposerBar.modelMaxWidthDp(320, buttons = 3)).isEqualTo(136)
        // A wide bar lets it reach its widest, and it never drops below its narrowest.
        assertThat(ComposerBar.modelMaxWidthDp(600, buttons = 1)).isEqualTo(180)
        assertThat(ComposerBar.modelMaxWidthDp(0, buttons = 0)).isEqualTo(96)
    }

    private fun modes(selected: String?, vararg extra: String) = ComposerState.PermissionModes(
        selected,
        (listOf("ask", "edits", "everything") + extra).map {
            ComposerState.PermissionOption(it, "Label of $it", selected = it == selected)
        }
    )

    @Test
    fun `the pill names any selected mode but Ask first`() {
        assertThat(ComposerBar.selectedMode(ComposerState())).isNull()
        assertThat(ComposerBar.selectedMode(ComposerState(permissionModes = modes("ask")))).isNull()
        assertThat(ComposerBar.selectedMode(ComposerState(permissionModes = modes(null)))).isNull()
        assertThat(ComposerBar.selectedMode(ComposerState(permissionModes = modes("edits")))?.value).isEqualTo("edits")
        assertThat(ComposerBar.selectedMode(ComposerState(permissionModes = modes("everything")))?.value)
            .isEqualTo("everything")
        // A mode this build doesn't know still shows, under the server's own label.
        assertThat(ComposerBar.selectedMode(ComposerState(permissionModes = modes("later", "later")))?.label)
            .isEqualTo("Label of later")
    }

    @Test
    fun `the pill's sheet shows only Changes`() {
        val sheet = ComposerBar.Sheet.CHANGES
        assertThat(sheet.permissions).isTrue()
        assertThat(sheet.attach).isFalse()
        assertThat(sheet.folded).isEmpty()
    }

    @Test
    fun `the globe keeps its fold width beside the pill`() {
        val state = everything.copy(permissionModes = modes("edits"))
        assertThat(ComposerBar.inBar(360, state, dictationSupported = true, modeDp = 72))
            .containsExactly(Control.DICTATE, Control.WEB_SEARCH)
        assertThat(ComposerBar.inBar(359, state, dictationSupported = true, modeDp = 72))
            .containsExactly(Control.DICTATE)
    }

    @Test
    fun `the microphone folds only when the pill and the chip's logo need its room`() {
        val state = everything.copy(permissionModes = modes("everything"))
        // "+", the 110dp pill, the 56dp chip with its 8dp gap, and Send: 262dp, then 44dp for the microphone.
        assertThat(ComposerBar.inBar(306, state, dictationSupported = true, modeDp = 110))
            .containsExactly(Control.DICTATE)
        assertThat(ComposerBar.inBar(305, state, dictationSupported = true, modeDp = 110)).isEmpty()
    }

    @Test
    fun `beside the pill the chip gives way to its logo and chevron, and only then`() {
        // A 412dp phone's 364dp bar: "+", the microphone, the globe, Everything, its gap and Send leave 70dp.
        assertThat(ComposerBar.modelMaxWidthDp(364, buttons = 3, modeDp = 110)).isEqualTo(70)
        // A 320dp phone's 272dp bar with "+" alone beside Everything.
        assertThat(ComposerBar.modelMaxWidthDp(272, buttons = 1, modeDp = 110)).isEqualTo(66)
        assertThat(ComposerBar.modelMaxWidthDp(100, buttons = 1, modeDp = 110)).isEqualTo(56)
        assertThat(ComposerBar.modelMaxWidthDp(100, buttons = 1, modeDp = 0)).isEqualTo(96)
    }

    @Test
    fun `the model's name goes rather than being only an ellipsis`() {
        assertThat(ComposerBar.modelNameFits(maxWidthDp = 96, chromeDp = 55)).isTrue()
        assertThat(ComposerBar.modelNameFits(maxWidthDp = 79, chromeDp = 55)).isTrue()
        assertThat(ComposerBar.modelNameFits(maxWidthDp = 78, chromeDp = 55)).isFalse()
    }

    @Test
    fun `+, the pill, the smallest chip and Send always fit a long label`() {
        // 272 (a 320dp phone) less "+", the 56dp chip, its gap and Send.
        assertThat(ComposerBar.modeMaxWidthDp(272)).isEqualTo(120)
        assertThat(ComposerBar.modeMaxWidthDp(0)).isEqualTo(44)
    }
}
