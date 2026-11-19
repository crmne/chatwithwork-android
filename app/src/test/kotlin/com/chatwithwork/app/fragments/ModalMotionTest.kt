package com.chatwithwork.app.fragments

import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** How modals come and go: Material 3's full-screen dialog on a phone, a dialog on a large window. */
class ModalMotionTest {
    @Test
    fun `a modal slides up over a still screen and slides away when closed`() {
        val anims = ModalMotion.anims(
            fromModal = false,
            toModal = true,
            replacesRoot = false,
            replacesCurrent = false
        )!!

        assertThat(anims.enter).isEqualTo(R.anim.modal_present)
        assertThat(anims.exit).isEqualTo(R.anim.modal_under)
        assertThat(anims.popExit).isEqualTo(R.anim.modal_dismiss)
    }

    @Test
    fun `New chat sent gives way to its chat at once, never sliding away from a page it then covers`() {
        assertThat(ModalMotion.anims(fromModal = true, toModal = false, replacesRoot = true, replacesCurrent = false))
            .isEqualTo(ModalMotion.Anims(0, 0, 0, 0))
    }

    @Test
    fun `the same modal page again swaps in place, and a step inside a modal slides in from the side`() {
        assertThat(ModalMotion.anims(fromModal = true, toModal = true, replacesRoot = false, replacesCurrent = true))
            .isEqualTo(ModalMotion.Anims(0, 0, 0, 0))
        assertThat(
            ModalMotion.anims(fromModal = true, toModal = true, replacesRoot = false, replacesCurrent = false)!!.enter
        )
            .isNotEqualTo(0)
    }

    @Test
    fun `pages outside modals keep Hotwire Native's own motion`() {
        assertThat(
            ModalMotion.anims(fromModal = false, toModal = false, replacesRoot = true, replacesCurrent = false)
        ).isNull()
        assertThat(
            ModalMotion.anims(fromModal = false, toModal = false, replacesRoot = false, replacesCurrent = false)
        ).isNull()
    }

    @Test
    fun `a modal is a dialog only in a medium or expanded window that isn't a phone on its side`() {
        assertThat(ModalFrame.isDialog(widthDp = 411, heightDp = 914)).isFalse()
        assertThat(ModalFrame.isDialog(widthDp = 914, heightDp = 411)).isFalse()
        assertThat(ModalFrame.isDialog(widthDp = 800, heightDp = 1280)).isTrue()
        assertThat(ModalFrame.isDialog(widthDp = 1280, heightDp = 800)).isTrue()
    }
}
