package com.chatwithwork.app.main

import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/**
 * Grows small controls' touch areas inside [parent] to [minDp] square, centred
 * on each control, without moving or redrawing anything: a tap in the space
 * around a 34dp button that lands on nothing else reaches the button, as
 * Android's 48dp minimum asks. [targets] are descendants of [parent], read at
 * every touch, so controls that come and go are always current. A tap on
 * another control still goes to that control: only touches nothing took
 * reach here.
 */
class TouchTargets(
    private val parent: ViewGroup,
    private val minDp: Int = MIN_TARGET_DP,
    private val targets: () -> List<View>
) : TouchDelegate(Rect(), parent) {
    private var current: TouchDelegate? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            current = nearest(event.x.toInt(), event.y.toInt())?.let { (view, area) -> TouchDelegate(area, view) }
        }
        val delegate = current ?: return false
        val handled = delegate.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            current = null
        }
        return handled
    }

    /** The grown areas, so accessibility services know where each control can be touched (API 29 and later). */
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getTouchDelegateInfo(): AccessibilityNodeInfo.TouchDelegateInfo {
        val areas = live().associate { view -> Region(area(view)) to view }
        return AccessibilityNodeInfo.TouchDelegateInfo(areas.ifEmpty { mapOf(Region() to parent) })
    }

    private fun live() = targets().filter { it.isShown && it.isEnabled && it.isClickable && it.width > 0 }

    private fun nearest(x: Int, y: Int): Pair<View, Rect>? = live()
        .map { it to area(it) }
        .filter { (_, area) -> area.contains(x, y) }
        .minByOrNull { (_, area) ->
            val dx = area.centerX() - x
            val dy = area.centerY() - y
            dx * dx + dy * dy
        }

    /** [view]'s bounds in [parent]'s coordinates, grown to [minDp] each way where it's smaller. */
    fun area(view: View): Rect {
        val bounds = Rect(0, 0, view.width, view.height)
        parent.offsetDescendantRectToMyCoords(view, bounds)
        val min = (minDp * parent.resources.displayMetrics.density).toInt()
        val growX = ((min - bounds.width()) / 2f).coerceAtLeast(0f)
        val growY = ((min - bounds.height()) / 2f).coerceAtLeast(0f)
        bounds.inset(-kotlin.math.ceil(growX).toInt(), -kotlin.math.ceil(growY).toInt())
        return bounds
    }

    companion object {
        const val MIN_TARGET_DP = 48
    }
}
