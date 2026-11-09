package com.chatwithwork.app.composer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.chatwithwork.app.bridge.dismissWithActivity
import com.chatwithwork.app.bridge.opensExpanded
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * The composer's "+" sheet: Camera, Photos (Android's photo picker, which
 * needs no permission), Files, Scan (ML Kit's document scanner, one PDF),
 * what the bar had no room for ([ComposerBar.Sheet.folded]): Dictate
 * when the page offers dictation, and Web search as a switch when the page
 * offers it; Narrow services when the page offers it, which opens the web's
 * dialog; and Changes, the modes the page offers. The mode pill opens it
 * with Changes alone ([ComposerBar.Sheet.CHANGES]).
 */
object AttachSheet {
    interface Choices {
        fun camera()

        fun photos()

        fun files()

        fun scan()

        fun dictate()

        fun webSearch(on: Boolean)

        fun narrow()

        fun permissionMode(value: String)
    }

    @SuppressLint("InflateParams") // A dialog's content has no parent until it's set.
    fun show(context: Context, state: ComposerState, sheet: ComposerBar.Sheet, choices: Choices): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val content = inflater.inflate(R.layout.sheet_attach, null)
        val tiles = content.findViewById<LinearLayout>(R.id.attach_tiles)
        val ink = MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurface)

        fun tile(symbol: String, label: Int, action: () -> Unit) {
            val view = inflater.inflate(R.layout.sheet_attach_tile, tiles, false)
            view.findViewById<ImageView>(
                R.id.attach_tile_icon
            ).setImageDrawable(SymbolDrawable(context, symbol, sizeDp = 26f, color = ink))
            view.findViewById<TextView>(R.id.attach_tile_label).setText(label)
            view.contentDescription = context.getString(label)
            view.setOnClickListener {
                dialog.dismiss()
                action()
            }
            tiles.addView(view)
        }

        if (sheet.attach) {
            tile("photo_camera", R.string.composer_camera, choices::camera)
            tile("photo_library", R.string.composer_photos, choices::photos)
            tile("attach_file", R.string.composer_files, choices::files)
            tile("document_scanner", R.string.composer_scan, choices::scan)
        }
        if (ComposerBar.Control.DICTATE in sheet.folded) tile("mic", R.string.composer_dictate, choices::dictate)
        tiles.isVisible = tiles.isNotEmpty()

        val search = state.webSearch?.takeIf { ComposerBar.Control.WEB_SEARCH in sheet.folded }
        val row = content.findViewById<View>(R.id.attach_web_search)
        row.isVisible = search != null
        if (search != null) {
            content.findViewById<ImageView>(
                R.id.attach_web_search_icon
            ).setImageDrawable(
                // The web menu's globe (icon_tag "globe"), as the bar's.
                ContextCompat.getDrawable(context, R.drawable.ic_globe)?.mutate()?.apply { setTint(ink) }
            )
            content.findViewById<TextView>(R.id.attach_web_search_note).apply {
                text = search.note
                isVisible = !search.note.isNullOrBlank()
            }
            content.findViewById<MaterialSwitch>(R.id.attach_web_search_switch).apply {
                isChecked = search.on
                isEnabled = !search.disabled
                setOnCheckedChangeListener { _, on -> choices.webSearch(on) }
            }
        }

        val narrowingRow = content.findViewById<LinearLayout>(R.id.attach_narrowing)
        val narrowing = state.narrowing?.takeIf { sheet.narrowing }
        narrowingRow.isVisible = narrowing != null
        if (narrowing != null) {
            // Styled as the modes' rows, with the web item's funnel (icon_tag "funnel-simple").
            val item = inflater.inflate(R.layout.action_sheet_item, narrowingRow, false)
            item.findViewById<TextView>(R.id.action_sheet_item_label).text = narrowing.title
            item.findViewById<TextView>(R.id.action_sheet_item_subtitle).apply {
                text = narrowing.note
                isVisible = !narrowing.note.isNullOrBlank()
            }
            item.findViewById<ImageView>(R.id.action_sheet_item_icon)
                .setImageDrawable(SymbolDrawable(context, "filter_alt", color = ink))
            item.setOnClickListener {
                dialog.dismiss()
                choices.narrow()
            }
            narrowingRow.addView(item)
        }

        val permissions = content.findViewById<LinearLayout>(R.id.attach_permissions)
        permissions.isVisible = sheet.permissions && state.permissionModes != null
        state.permissionModes?.options?.takeIf { sheet.permissions }?.forEach { option ->
            val item = inflater.inflate(R.layout.action_sheet_item, permissions, false)
            item.findViewById<TextView>(R.id.action_sheet_item_label).text = option.label
            item.findViewById<TextView>(R.id.action_sheet_item_subtitle).apply {
                text = option.description
                isVisible = !option.description.isNullOrBlank()
            }
            item.findViewById<ImageView>(R.id.action_sheet_item_icon).apply {
                val symbol = when (option.value) {
                    "everything" -> "report"
                    "edits" -> "edit"
                    else -> "back_hand"
                }
                setImageDrawable(SymbolDrawable(context, symbol, color = ink))
                // The row's own tint draws the others muted; Allow everything reads as the bar's pill does.
                if (option.value == "everything") {
                    ImageViewCompat.setImageTintList(
                        this,
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.lw_negative_ink))
                    )
                }
            }
            item.findViewById<ImageView>(R.id.action_sheet_item_check).isVisible = option.selected
            item.isSelected = option.selected
            item.isEnabled = !option.disabled
            item.alpha = if (option.disabled) 0.5f else 1f
            item.setOnClickListener {
                dialog.dismiss()
                choices.permissionMode(option.value)
            }
            permissions.addView(item)
        }

        dialog.setContentView(content)
        dialog.opensExpanded()
        dialog.dismissWithActivity(context)
        dialog.show()
        return dialog
    }
}
