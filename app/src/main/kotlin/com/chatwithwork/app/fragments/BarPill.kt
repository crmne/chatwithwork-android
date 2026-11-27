package com.chatwithwork.app.fragments

import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import com.chatwithwork.app.R
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * The top bar's actions on one floating pill (TopBar): the Material 3
 * surface the round navigation button wears too (`toolbar_round_button`),
 * fully rounded, with the hairline and the soft lift of Live Wire's raised
 * surfaces. Icon actions keep their 48dp circles inside it; a text action
 * (a form's Save, the organization chooser) gets room at its sides.
 *
 * Bridge components add and remove the bar's items as pages connect, and the
 * Toolbar makes its ActionMenuView on the first, so this follows the
 * Toolbar's children rather than styling them once.
 */
object BarPill {
    fun install(toolbar: Toolbar) {
        toolbar.children.forEach(::place)
        toolbar.setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) = place(child)

                override fun onChildViewRemoved(parent: View, child: View) = Unit
            }
        )
    }

    private fun place(child: View) {
        when (child) {
            is ActionMenuView -> style(child)

            // The navigation button (Toolbar makes it on the first icon): its style's margin isn't read, since
            // Toolbar gives it layout params of its own.
            is ImageButton ->
                child.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = child.resources.getDimensionPixelSize(R.dimen.top_bar_edge)
                }
        }
    }

    private fun style(menu: ActionMenuView) {
        if (menu.getTag(R.id.bar_pill) != null) return
        menu.setTag(R.id.bar_pill, true)
        val context = menu.context
        val res = context.resources
        val inset = res.getDimensionPixelSize(R.dimen.top_bar_button_inset)
        val surface =
            MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(ShapeAppearanceModel.PILL).build())
                .apply {
                    fillColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.lw_surface))
                    setStroke(
                        res.getDimension(R.dimen.top_bar_button_stroke),
                        ContextCompat.getColor(context, R.color.lw_line)
                    )
                }
        menu.background = InsetDrawable(surface, 0, inset, 0, inset)
        menu.elevation = res.getDimension(R.dimen.top_bar_button_elevation)
        menu.outlineSpotShadowColor = ContextCompat.getColor(context, R.color.lw_shadow)
        menu.outlineAmbientShadowColor = ContextCompat.getColor(context, R.color.lw_shadow)
        menu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = res.getDimensionPixelSize(R.dimen.top_bar_edge)
        }
        // A text action would otherwise touch the pill's ends.
        val textRoom = res.getDimensionPixelSize(R.dimen.top_bar_text_action_padding)
        menu.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            (view as ViewGroup).children.forEach { item ->
                val hasText = (item as? android.widget.TextView)?.text?.isNotEmpty() == true
                val wanted = if (hasText) textRoom else 0
                if (item.paddingStart != wanted && item !is android.widget.ImageView) {
                    item.post { item.updatePaddingRelative(start = wanted, end = wanted) }
                }
            }
        }
    }
}
