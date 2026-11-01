package com.chatwithwork.app.composer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/**
 * Puts the keyboard away when a finger drags down over the page or the
 * composer. It only watches: the touch still reaches the page, which scrolls
 * as usual, so tapping, selecting, and scrolling up are left alone.
 */
class KeyboardSwipeLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val distance = DISTANCE_DP * resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var watching = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                watching = true
            }

            MotionEvent.ACTION_POINTER_DOWN -> watching = false

            // A pinch.

            MotionEvent.ACTION_MOVE ->
                if (watching && isSwipeDown(event.x - startX, event.y - startY, distance)) {
                    watching = false
                    hideKeyboard()
                }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun hideKeyboard() {
        val insets = ViewCompat.getRootWindowInsets(this) ?: return
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
    }

    companion object {
        private const val DISTANCE_DP = 24f
        private const val STEEPNESS = 1.5f

        /** Far enough down, and more down than sideways, so a sideways drag isn't one. */
        fun isSwipeDown(dx: Float, dy: Float, distance: Float): Boolean = dy >= distance && dy >= abs(dx) * STEEPNESS
    }
}
