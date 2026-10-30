package com.chatwithwork.app.composer

/**
 * Which of the composer's own controls show in its bar, one tap away, and
 * which fold into "+", as the web composer's folding bar does
 * (components/composer.css, `.chat-prompt__bar--folding`): the microphone
 * and the web search globe while there's room, web search folding first and
 * dictation after it as the bar narrows, so the model chip keeps room for a
 * short name and Send never shrinks. Attaching (Camera, Photos, Files, Scan)
 * stays behind "+", which on a phone is the paperclip's menu; it turns into
 * the paperclip when that's all it holds.
 */
object ComposerBar {
    enum class Control { DICTATE, WEB_SEARCH }

    private const val BUTTON_DP = 44
    private const val CHIP_GAP_DP = 8
    private const val CHIP_MIN_DP = 96
    private const val CHIP_MAX_DP = 180

    // "+", the model chip at its narrowest with its gap, and Send.
    private const val BASE_DP = BUTTON_DP + CHIP_MIN_DP + CHIP_GAP_DP + BUTTON_DP

    /** The controls the page offers now that have a button of their own. */
    fun offered(state: ComposerState, dictationSupported: Boolean = Dictation.supported): List<Control> = listOfNotNull(
        Control.DICTATE.takeIf { state.dictation != null && dictationSupported },
        Control.WEB_SEARCH.takeIf { state.webSearch != null }
    )

    /** What shows in a bar [widthDp] wide (inside the composer's box), most used first. */
    fun inBar(widthDp: Int, state: ComposerState, dictationSupported: Boolean = Dictation.supported): Set<Control> {
        val offered = offered(state, dictationSupported)
        val room = ((widthDp - BASE_DP) / BUTTON_DP).coerceAtLeast(0)
        return offered.take(room).toSet()
    }

    /**
     * How wide the model chip may grow in a bar [widthDp] wide with [buttons]
     * round buttons showing before it: what Send and the chip's gap leave,
     * between its narrowest and its widest. The chip measures before Send,
     * so without this cap a long model name would push Send off the bar.
     */
    fun modelMaxWidthDp(widthDp: Int, buttons: Int): Int =
        (widthDp - buttons * BUTTON_DP - CHIP_GAP_DP - BUTTON_DP).coerceIn(CHIP_MIN_DP, CHIP_MAX_DP)

    /** What "+" holds: attaching, when the page takes files, and whatever didn't fit in the bar. */
    fun inSheet(state: ComposerState, bar: Set<Control>, dictationSupported: Boolean = Dictation.supported): Sheet =
        Sheet(
            attach = state.acceptsFiles,
            folded = offered(state, dictationSupported).filterNot { it in bar }.toSet(),
            permissions = state.permissionModes != null
        )

    data class Sheet(val attach: Boolean, val folded: Set<Control>, val permissions: Boolean = false) {
        val empty: Boolean get() = !attach && folded.isEmpty() && !permissions

        /** It holds only attaching: "+" is the paperclip. */
        val attachOnly: Boolean get() = attach && folded.isEmpty() && !permissions
    }
}
