package com.chatwithwork.app.composer

/**
 * What the composer's rainbow shows for a state, the web's composer.css in
 * numbers: the edge's 70% at rest, full with focus, flowing while an answer
 * runs or a question is on its way (the web form's aria-busy), amber while a
 * change waits, and the glow 16% at rest, 30% with focus, 5% locked, 14%
 * amber. The glow is the edge's own colours blurred, so it flows and turns
 * amber with it. Amber outranks both flowing and the locked dim.
 */
data class ComposerLook(
    val locked: Boolean = false,
    val working: Boolean = false,
    val waiting: Boolean = false,
    val focused: Boolean = false,
    val sending: Boolean = false
) {
    enum class Edge { RAINBOW, FLOWING, ATTENTION }

    val edge: Edge get() = if (waiting) {
        Edge.ATTENTION
    } else if (working || sending) {
        Edge.FLOWING
    } else {
        Edge.RAINBOW
    }

    val edgeOpacity: Float get() = if (focused) {
        1f
    } else if (locked) {
        0.6f
    } else {
        0.7f
    }

    val glowOpacity: Float
        get() = when {
            focused -> if (locked) 0.08f else 0.3f
            waiting -> 0.14f
            locked -> 0.05f
            else -> 0.16f
        }

    companion object {
        /** A lock disables the field but leaves it focused, and a locked composer can't be typed in. */
        fun of(state: ComposerState, inputFocused: Boolean, sending: Boolean = false) = ComposerLook(
            state.locked,
            state.working,
            state.waiting,
            inputFocused && !state.locked,
            sending && !state.locked
        )
    }
}
