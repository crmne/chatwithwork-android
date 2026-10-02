package com.chatwithwork.app.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.R as AppCompatR
import androidx.core.view.isVisible
import com.chatwithwork.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors

/**
 * A list of actions in a bottom sheet, with an optional title: Android's
 * action sheet. Used for a context menu with nothing to anchor to, and for
 * choosing among things, like the organization switcher.
 */
object ActionSheet {
    class Action(
        val title: String,
        val image: String? = null,
        val destructive: Boolean = false,
        val disabled: Boolean = false,
        val checked: Boolean = false
    )

    @SuppressLint("InflateParams") // A dialog's content has no parent until it's set.
    fun show(
        context: Context,
        title: String?,
        actions: List<Action>,
        onChoose: (index: Int) -> Unit
    ): BottomSheetDialog {
        val inflater = LayoutInflater.from(context)
        val content = inflater.inflate(R.layout.action_sheet, null)
        val heading = content.findViewById<TextView>(R.id.action_sheet_title)
        val list = content.findViewById<LinearLayout>(R.id.action_sheet_items)

        heading.text = title
        heading.isVisible = !title.isNullOrBlank()

        val dialog = BottomSheetDialog(context)
        val error = MaterialColors.getColor(content, AppCompatR.attr.colorError)

        actions.forEachIndexed { index, action ->
            val row = inflater.inflate(R.layout.action_sheet_item, list, false)
            val label = row.findViewById<TextView>(R.id.action_sheet_item_label)
            val icon = row.findViewById<ImageView>(R.id.action_sheet_item_icon)
            val check = row.findViewById<ImageView>(R.id.action_sheet_item_check)

            label.text = action.title
            val symbol = SymbolDrawable.from(context, action.image)
            when {
                symbol != null -> icon.setImageDrawable(symbol)

                // No icons in the sheet at all: the labels line up with the title.
                actions.none { it.image != null } -> icon.visibility = View.GONE

                // Some icons: this label lines up with the others.
                else -> icon.visibility = View.INVISIBLE
            }
            if (action.destructive) {
                label.setTextColor(error)
                icon.imageTintList = ColorStateList.valueOf(error)
            }
            check.isVisible = action.checked
            row.isSelected = action.checked
            row.isEnabled = !action.disabled
            row.alpha = if (row.isEnabled) 1f else DISABLED_ALPHA
            row.setOnClickListener {
                dialog.dismiss()
                onChoose(index)
            }
            list.addView(row)
        }

        dialog.setContentView(content)
        dialog.show()
        return dialog
    }

    private const val DISABLED_ALPHA = 0.38f
}
