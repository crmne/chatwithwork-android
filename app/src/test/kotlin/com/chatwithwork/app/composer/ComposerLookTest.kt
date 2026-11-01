package com.chatwithwork.app.composer

import com.chatwithwork.app.composer.ComposerLook.Edge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The web composer's numbers (components/composer.css), state by state. */
class ComposerLookTest {
    @Test
    fun `at rest the rainbow shows at 70 percent in a 14 percent glow`() {
        val rest = ComposerLook()
        assertThat(rest.edge).isEqualTo(Edge.RAINBOW)
        assertThat(rest.edgeOpacity).isEqualTo(0.7f)
        assertThat(rest.glowOpacity).isEqualTo(0.14f)
    }

    @Test
    fun `focus brightens the edge and the glow`() {
        val focused = ComposerLook(focused = true)
        assertThat(focused.edgeOpacity).isEqualTo(1f)
        assertThat(focused.glowOpacity).isEqualTo(0.3f)
    }

    @Test
    fun `the edge flows while an answer runs, the glow unchanged`() {
        val working = ComposerLook(working = true)
        assertThat(working.edge).isEqualTo(Edge.FLOWING)
        assertThat(working.glowOpacity).isEqualTo(0.14f)
    }

    @Test
    fun `a lock dims both`() {
        val locked = ComposerLook(locked = true)
        assertThat(locked.edgeOpacity).isEqualTo(0.6f)
        assertThat(locked.glowOpacity).isEqualTo(0.05f)
    }

    @Test
    fun `amber outranks the flow and the lock's dim glow`() {
        val waiting = ComposerLook(locked = true, working = true, waiting = true)
        assertThat(waiting.edge).isEqualTo(Edge.ATTENTION)
        assertThat(waiting.glowOpacity).isEqualTo(0.14f)
        assertThat(ComposerLook(waiting = true, focused = true).glowOpacity).isEqualTo(0.3f)
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
    fun `the blur undoes Skia's radius conversion`() {
        assertThat(ComposerGlow.radiusFor(20f)).isWithin(0.01f).of(33.77f)
    }
}
