package com.chatwithwork.app.composer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
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
 * Dictate when the page offers dictation, and Web search as a switch when
 * the page offers it.
 */
object AttachSheet {
    interface Choices {
        fun camera()

        fun photos()

        fun files()

        fun scan()

        fun dictate()

        fun webSearch(on: Boolean)
    }

    @SuppressLint("InflateParams") // A dialog's content has no parent until it's set.
    fun show(context: Context, state: ComposerState, choices: Choices): BottomSheetDialog {
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

        if (state.acceptsFiles) {
            tile("photo_camera", R.string.composer_camera, choices::camera)
            tile("photo_library", R.string.composer_photos, choices::photos)
            tile("attach_file", R.string.composer_files, choices::files)
            tile("document_scanner", R.string.composer_scan, choices::scan)
        }
        if (state.dictation != null && Dictation.supported) tile("mic", R.string.composer_dictate, choices::dictate)
        tiles.isVisible = tiles.isNotEmpty()

        val search = state.webSearch
        val row = content.findViewById<View>(R.id.attach_web_search)
        row.isVisible = search != null
        if (search != null) {
            content.findViewById<ImageView>(
                R.id.attach_web_search_icon
            ).setImageDrawable(SymbolDrawable(context, "language", color = ink))
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

        dialog.setContentView(content)
        dialog.opensExpanded()
        dialog.dismissWithActivity(context)
        dialog.show()
        return dialog
    }
}
