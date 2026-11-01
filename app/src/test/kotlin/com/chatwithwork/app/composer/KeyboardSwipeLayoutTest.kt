package com.chatwithwork.app.composer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSwipeLayoutTest {
    @Test
    fun `a drag down past the distance is a swipe`() {
        assertTrue(KeyboardSwipeLayout.isSwipeDown(dx = 4f, dy = 60f, distance = 60f))
    }

    @Test
    fun `a short drag, a drag up, and a sideways drag are not`() {
        assertFalse(KeyboardSwipeLayout.isSwipeDown(dx = 0f, dy = 59f, distance = 60f))
        assertFalse(KeyboardSwipeLayout.isSwipeDown(dx = 0f, dy = -90f, distance = 60f))
        assertFalse(KeyboardSwipeLayout.isSwipeDown(dx = 80f, dy = 70f, distance = 60f))
    }
}
