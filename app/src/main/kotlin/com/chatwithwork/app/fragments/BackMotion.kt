package com.chatwithwork.app.fragments

import android.os.SystemClock

/**
 * The system back at a place opens the home, Decisions, as the root
 * (GoingBack): underneath a replace like any place, it moves as a pop
 * does, the home sliding in from the left and the place left sliding away
 * to the right, so it never reads as going forward.
 */
object BackMotion {
    private var expected: String? = null
    private var expectedAt = 0L

    fun expect(location: String, now: Long = SystemClock.uptimeMillis()) {
        expected = location
        expectedAt = now
    }

    /** Whether the visit to [location] is back's, taken once. */
    fun take(location: String, now: Long = SystemClock.uptimeMillis()): Boolean {
        val taken = expected == location && now - expectedAt in 0..EXPECTED_FOR_MS
        if (taken || now - expectedAt > EXPECTED_FOR_MS) expected = null
        return taken
    }

    private const val EXPECTED_FOR_MS = 5_000L
}
