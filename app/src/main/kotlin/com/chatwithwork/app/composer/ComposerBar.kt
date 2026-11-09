package com.chatwithwork.app.composer

import androidx.annotation.DrawableRes
import com.chatwithwork.app.R

/**
 * What shows in the composer's bar, one tap away, and what folds into "+", as
 * the web composer's folding bar does (components/composer.css,
 * `.chat-prompt__bar--folding`). Left to right: "+", always; the microphone and
 * the web search globe while there's room, web search folding first, on the
 * same phones as the web's, and dictation after it as the bar narrows; and the
 * mode pill, whenever the selected mode isn't Ask first. The model chip's name
 * gives way next, so Send and the pill never leave the bar. Attaching (Camera,
 * Photos, Files, Scan) and the modes stay behind "+".
 */
object ComposerBar {
    enum class Control { DICTATE, WEB_SEARCH }

    private const val ASK_MODE = "ask"

    private const val BUTTON_DP = 44
    private const val CHIP_GAP_DP = 8
    private const val CHIP_MIN_DP = 96

    // Beside the pill the chip may shrink to its logo and chevron: 16dp each, 8dp and 7dp padding, 4dp gaps.
    private const val CHIP_MIN_BESIDE_MODE_DP = 56
    private const val CHIP_MAX_DP = 180

    // Less room than this for the model's name and it would only be an ellipsis, so the chip drops it.
    private const val MODEL_NAME_MIN_DP = 24

    // The web's `@container composer-bar (width < 24rem)` measures the whole box, in a phone browser the screen less
    // 24px, so it folds below a screen 408 wide (CSS pixels are dp). This bar is the screen less 48dp, its 16dp gutters
    // and the box's 8dp padding, so the same phone folds it at 360.
    private const val WEB_SEARCH_MIN_DP = 360

    /**
     * The globe's icon: Phosphor's filled globe while web search is on, its outline when off or when it can't be
     * switched now, as the web's toggle. The shape, not only the tone, says it's on, for people who can't tell the inks
     * apart.
     */
    @DrawableRes
    fun webSearchIcon(search: ComposerState.WebSearch): Int =
        if (search.on && !search.disabled) R.drawable.ic_globe_fill else R.drawable.ic_globe

    /** The mode the pill names: the selected one, unless it's Ask first, which shows nothing. */
    fun selectedMode(state: ComposerState): ComposerState.PermissionOption? =
        state.permissionModes?.options?.firstOrNull { it.selected }?.takeIf { it.value != ASK_MODE }

    /** The controls the page offers now that have a button of their own. */
    fun offered(state: ComposerState, dictationSupported: Boolean = Dictation.supported): List<Control> = listOfNotNull(
        Control.DICTATE.takeIf { state.dictation != null && dictationSupported },
        Control.WEB_SEARCH.takeIf { state.webSearch != null }
    )

    /**
     * What shows in a bar [widthDp] wide (inside the composer's box), most used first, with a pill [modeDp] wide
     * beside them, 0 without one. "+", the pill, the model chip at its narrowest with its gap, and Send come first.
     */
    fun inBar(
        widthDp: Int,
        state: ComposerState,
        dictationSupported: Boolean = Dictation.supported,
        modeDp: Int = 0
    ): Set<Control> {
        val offered = offered(state, dictationSupported)
        val taken = BUTTON_DP + modeDp + chipMinDp(modeDp) + CHIP_GAP_DP + BUTTON_DP
        val room = ((widthDp - taken) / BUTTON_DP).coerceAtLeast(0)
        return offered.filter { it != Control.WEB_SEARCH || widthDp >= WEB_SEARCH_MIN_DP }.take(room).toSet()
    }

    /**
     * How wide the model chip may grow in a bar [widthDp] wide with [buttons]
     * buttons and a pill [modeDp] wide before it: what Send and the chip's gap
     * leave, between its narrowest and its widest. The chip measures before
     * Send, so without this cap a long model name would push Send off the bar.
     */
    fun modelMaxWidthDp(widthDp: Int, buttons: Int, modeDp: Int = 0): Int =
        (widthDp - buttons * BUTTON_DP - modeDp - CHIP_GAP_DP - BUTTON_DP).coerceIn(chipMinDp(modeDp), CHIP_MAX_DP)

    /** Whether a chip [maxWidthDp] wide, [chromeDp] of it logo, chevron and padding, has room for the model's name. */
    fun modelNameFits(maxWidthDp: Int, chromeDp: Int): Boolean = maxWidthDp - chromeDp >= MODEL_NAME_MIN_DP

    /** The widest the pill may be: a long unknown label ellipsizes before it takes "+", the chip or Send's room. */
    fun modeMaxWidthDp(widthDp: Int): Int =
        (widthDp - BUTTON_DP - CHIP_MIN_BESIDE_MODE_DP - CHIP_GAP_DP - BUTTON_DP).coerceAtLeast(BUTTON_DP)

    private fun chipMinDp(modeDp: Int) = if (modeDp > 0) CHIP_MIN_BESIDE_MODE_DP else CHIP_MIN_DP

    /**
     * What "+" holds: attaching, when the page takes files, whatever didn't fit in the bar, Narrow services when the
     * page offers it, and the modes.
     */
    fun inSheet(state: ComposerState, bar: Set<Control>, dictationSupported: Boolean = Dictation.supported): Sheet =
        Sheet(
            attach = state.acceptsFiles,
            folded = offered(state, dictationSupported).filterNot { it in bar }.toSet(),
            permissions = state.permissionModes != null,
            narrowing = state.narrowing != null
        )

    data class Sheet(
        val attach: Boolean,
        val folded: Set<Control>,
        val permissions: Boolean = false,
        val narrowing: Boolean = false
    ) {
        val empty: Boolean get() = !attach && folded.isEmpty() && !permissions && !narrowing

        companion object {
            /** What the mode pill opens: the same sheet, showing only Changes. */
            val CHANGES = Sheet(attach = false, folded = emptySet(), permissions = true)
        }
    }
}
