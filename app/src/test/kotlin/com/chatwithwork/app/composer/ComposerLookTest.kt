package com.chatwithwork.app.composer

import com.chatwithwork.app.composer.ComposerLook.Edge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The web composer's numbers (components/composer.css), state by state. */
class ComposerLookTest {
    @Test
    fun `at rest the rainbow shows at 70 percent in a 16 percent glow`() {
        val rest = ComposerLook()
        assertThat(rest.edge).isEqualTo(Edge.RAINBOW)
        assertThat(rest.edgeOpacity).isEqualTo(0.7f)
        assertThat(rest.glowOpacity).isEqualTo(0.16f)
    }

    @Test
    fun `focus brightens the edge and the glow`() {
        val focused = ComposerLook(focused = true)
        assertThat(focused.edge).isEqualTo(Edge.RAINBOW)
        assertThat(focused.edgeOpacity).isEqualTo(1f)
        assertThat(focused.glowOpacity).isEqualTo(0.3f)
    }

    @Test
    fun `the edge and its glow flow while an answer runs, as bright as at rest`() {
        val working = ComposerLook(working = true)
        assertThat(working.edge).isEqualTo(Edge.FLOWING)
        assertThat(working.edgeOpacity).isEqualTo(0.7f)
        assertThat(working.glowOpacity).isEqualTo(0.16f)
    }

    @Test
    fun `a question on its way flows as an answer does`() {
        assertThat(ComposerLook(sending = true).edge).isEqualTo(Edge.FLOWING)
        assertThat(ComposerLook.of(ComposerState(), inputFocused = false, sending = true).edge).isEqualTo(Edge.FLOWING)
        assertThat(ComposerLook.of(ComposerState(locked = true), inputFocused = false, sending = true).edge)
            .isEqualTo(Edge.RAINBOW)
    }

    @Test
    fun `a lock dims both`() {
        val locked = ComposerLook(locked = true)
        assertThat(locked.edgeOpacity).isEqualTo(0.6f)
        assertThat(locked.glowOpacity).isEqualTo(0.05f)
    }

    @Test
    fun `a change waiting turns both amber at 14 percent, over the flow and the lock's dim glow`() {
        val waiting = ComposerLook(waiting = true)
        assertThat(waiting.edge).isEqualTo(Edge.ATTENTION)
        assertThat(waiting.edgeOpacity).isEqualTo(0.7f)
        assertThat(waiting.glowOpacity).isEqualTo(0.14f)

        val over = ComposerLook(locked = true, working = true, waiting = true, sending = true)
        assertThat(over.edge).isEqualTo(Edge.ATTENTION)
        assertThat(over.glowOpacity).isEqualTo(0.14f)
        assertThat(ComposerLook(waiting = true, focused = true).glowOpacity).isEqualTo(0.3f)
    }

    @Test
    fun `each bridge state maps to its look`() {
        fun look(state: ComposerState, focused: Boolean = false) = ComposerLook.of(state, focused)

        assertThat(look(ComposerState()).edge).isEqualTo(Edge.RAINBOW)
        assertThat(look(ComposerState(), focused = true).edgeOpacity).isEqualTo(1f)
        assertThat(look(ComposerState(working = true)).edge).isEqualTo(Edge.FLOWING)
        assertThat(look(ComposerState(working = true, stopping = true)).edge).isEqualTo(Edge.FLOWING)
        assertThat(look(ComposerState(waiting = true)).edge).isEqualTo(Edge.ATTENTION)
        assertThat(look(ComposerState(working = true, waiting = true)).edge).isEqualTo(Edge.ATTENTION)
    }

    @Test
    fun `a lock arriving while the field has focus keeps neither focus nor its brightness`() {
        val locked = ComposerLook.of(ComposerState(locked = true), inputFocused = true)
        assertThat(locked.edgeOpacity).isEqualTo(0.6f)
        assertThat(locked.glowOpacity).isEqualTo(0.05f)

        val waiting = ComposerLook.of(ComposerState(locked = true, waiting = true), inputFocused = true)
        assertThat(waiting.edge).isEqualTo(Edge.ATTENTION)
        assertThat(waiting.glowOpacity).isEqualTo(0.14f)
    }

    @Test
    fun `the glow is the box itself blurred by 22`() {
        assertThat(ComposerGlow.GLOW_RADIUS_DP).isEqualTo(ComposerBackground.RADIUS_DP)
        assertThat(ComposerGlow.GLOW_SIGMA_DP).isEqualTo(22f)
        assertThat(ComposerGlow.radiusFor(22f)).isWithin(0.01f).of(37.24f)
    }
}
