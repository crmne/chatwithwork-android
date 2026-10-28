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

    // "+" (44), the model chip at its narrowest (96) with its gap (8), and Send (44).
    private const val BASE_DP = 44 + 96 + 8 + 44
    private const val BUTTON_DP = 44

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

    /** What "+" holds: attaching, when the page takes files, and whatever didn't fit in the bar. */
    fun inSheet(state: ComposerState, bar: Set<Control>, dictationSupported: Boolean = Dictation.supported): Sheet =
        Sheet(
            attach = state.acceptsFiles,
            folded = offered(state, dictationSupported).filterNot { it in bar }.toSet()
        )

    data class Sheet(val attach: Boolean, val folded: Set<Control>) {
        val empty: Boolean get() = !attach && folded.isEmpty()

        /** It holds only attaching: "+" is the paperclip. */
        val attachOnly: Boolean get() = attach && folded.isEmpty()
    }
}
