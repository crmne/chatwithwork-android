package com.chatwithwork.app.bridge

import android.content.res.ColorStateList
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.view.MenuItemCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFragmentLifecycle
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.navigation.destinations.HotwireDestination

/**
 * A bridge component that puts actions in the screen's own top app bar, as
 * native menu items: icon buttons with ripples, tooltips, and the overflow
 * menu, instead of web buttons in the page.
 *
 * Each component owns one menu group, so components never remove each other's
 * items, and redraws its items whenever the fragment's view is recreated (the
 * toolbar is new then, but the component and its last messages survive).
 */
abstract class ToolbarComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate),
    BridgeComponentFragmentLifecycle {
    protected val destination: HotwireDestination
        get() = bridgeDelegate.destination

    protected val toolbar: Toolbar?
        get() = if (destination.fragment.view != null) destination.toolbarForNavigation() else null

    /** The menu group this component's items live in. */
    protected abstract val menuGroup: Int

    /** Draws the component's items from its current state. */
    protected abstract fun render(toolbar: Toolbar)

    protected fun redraw() {
        val toolbar = toolbar ?: return
        toolbar.menu.removeGroup(menuGroup)
        render(toolbar)
    }

    protected fun clear() {
        toolbar?.menu?.removeGroup(menuGroup)
    }

    override fun onViewCreated() {
        redraw()
    }

    override fun onDestroyView() {}

    /** The color of toolbar icons: the page's choice if it sent one, else Material's. */
    @ColorInt
    protected fun iconColor(toolbar: Toolbar, hex: String?): Int =
        parseColor(hex) ?: MaterialColors.getColor(toolbar, MaterialR.attr.colorOnSurfaceVariant)

    protected fun MenuItem.describe(title: String): MenuItem = apply {
        MenuItemCompat.setContentDescription(this, title)
        MenuItemCompat.setTooltipText(this, title)
    }

    protected fun MenuItem.tint(@ColorInt color: Int): MenuItem = apply {
        MenuItemCompat.setIconTintList(this, ColorStateList.valueOf(color))
    }

    companion object {
        // Menu groups, one per component, and the order items appear in.
        const val GROUP_SEARCH = 0x0C1
        const val GROUP_FORM = 0x0C2
        const val GROUP_BUTTON = 0x0C3
        const val GROUP_SHARE = 0x0C4
        const val GROUP_MENU = 0x0C5

        const val ORDER_SEARCH = 10
        const val ORDER_FORM = 20
        const val ORDER_BUTTON = 30
        const val ORDER_SHARE = 60
        const val ORDER_MENU = 90

        @ColorInt
        fun parseColor(hex: String?): Int? {
            val clean = hex?.trim()?.removePrefix("#") ?: return null
            if (clean.length != 6 && clean.length != 8) return null
            return runCatching { "#$clean".toColorInt() }.getOrNull()
        }
    }
}
