package com.chatwithwork.app.fragments

import com.chatwithwork.app.R
import dev.hotwire.navigation.R as HotwireR

/**
 * How screens move as modals come and go, in place of Hotwire Native's own
 * (a short fade and nudge from below for every modal): Material 3's
 * full-screen dialog, which [ModalFrame] draws.
 *
 * - Opening a modal: the screen under it stays still ([R.anim.modal_under])
 *   while the modal's card slides up over it ([R.anim.modal_present]);
 *   closing it, the card slides away ([R.anim.modal_dismiss]) off the screen,
 *   back at once beneath it.
 * - A screen pushed inside a modal (sign-up after sign-in) slides in from the
 *   side, with back, as a step does; one replacing the modal's page (New chat
 *   opened again, with shared words) swaps in place, and slides away
 *   when closed as any modal does.
 * - A modal giving way to a place (New chat's first message, New bot's
 *   sentence: the server sends the chat, which replaces the root) shows the
 *   place at once, never the form sliding away from a page it then covers.
 */
object ModalMotion {
    data class Anims(val enter: Int, val exit: Int, val popEnter: Int, val popExit: Int)

    /**
     * The animations for a visit from a screen ([fromModal]) to one in
     * [toModal]'s context, that [replacesRoot] or [replacesCurrent] (the same
     * page again); null leaves Hotwire Native's own.
     */
    fun anims(fromModal: Boolean, toModal: Boolean, replacesRoot: Boolean, replacesCurrent: Boolean): Anims? = when {
        !fromModal && toModal -> Anims(R.anim.modal_present, R.anim.modal_under, NONE, R.anim.modal_dismiss)

        fromModal && toModal && replacesCurrent -> Anims(NONE, NONE, NONE, R.anim.modal_dismiss)

        fromModal && toModal ->
            Anims(
                HotwireR.anim.enter_slide_in_right,
                HotwireR.anim.exit_slide_out_left,
                HotwireR.anim.enter_slide_in_left,
                HotwireR.anim.exit_slide_out_right
            )

        fromModal && replacesRoot -> Anims(NONE, NONE, NONE, NONE)

        else -> null
    }

    private const val NONE = 0
}
