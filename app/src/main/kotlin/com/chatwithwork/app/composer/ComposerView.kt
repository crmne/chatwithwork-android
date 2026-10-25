package com.chatwithwork.app.composer

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors

/**
 * The native composer: "+", the field, the model, and Send or Stop. It only
 * draws a [ComposerState] and says what the person did ([Actions]); the
 * composer component turns that into replies to the web composer, which keeps
 * every rule. Words stay here until they're sent.
 */
class ComposerView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    interface Actions {
        fun send(text: String)

        fun stop()

        fun openSheet()

        fun chooseModel()

        fun remove(id: String)
    }

    var actions: Actions? = null

    var state: ComposerState = ComposerState()
        private set

    private val lock by lazy { findViewById<TextView>(R.id.composer_lock) }
    private val input by lazy { findViewById<EditText>(R.id.composer_field) }
    private val plus by lazy { findViewById<ImageButton>(R.id.composer_plus) }
    private val model by lazy { findViewById<TextView>(R.id.composer_model) }
    private val send by lazy { findViewById<ImageButton>(R.id.composer_send) }
    private val filesScroll by lazy { findViewById<View>(R.id.composer_files_scroll) }
    private val files by lazy { findViewById<LinearLayout>(R.id.composer_files) }

    val text: String get() = input.text?.toString().orEmpty()

    override fun onFinishInflate() {
        super.onFinishInflate()
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        plus.setImageDrawable(SymbolDrawable(context, "add", color = ink))
        plus.setOnClickListener { actions?.openSheet() }
        model.setOnClickListener { actions?.chooseModel() }
        send.setOnClickListener { onSendOrStop() }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) = renderButton()
            }
        )
    }

    fun show(state: ComposerState) {
        this.state = state
        isVisible = true
        input.hint = state.placeholder.ifBlank { context.getString(R.string.composer_placeholder) }
        input.isEnabled = !state.locked
        plus.isEnabled = !state.locked
        plus.alpha = if (state.locked) DISABLED_ALPHA else 1f

        lock.isVisible = state.locked && !state.lockReason.isNullOrBlank()
        lock.text = state.lockReason
        lock.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SymbolDrawable(context, "lock", sizeDp = 16f, color = lock.currentTextColor).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            },
            null,
            null,
            null
        )

        val selected = state.selectedModel
        model.isVisible = selected != null && !state.locked && !state.working
        model.text = selected?.name
        model.contentDescription = selected?.let { context.getString(R.string.composer_model_description, it.name) }
        model.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null,
            null,
            SymbolDrawable(context, "unfold_more", sizeDp = 16f, color = model.currentTextColor).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            },
            null
        )

        renderFiles()
        renderButton()
    }

    /** Adds dictated (or shared) words to what's typed. */
    fun append(words: String) {
        val current = text
        val joined = if (current.isBlank()) words else current.trimEnd() + " " + words
        input.setText(joined)
        input.setSelection(joined.length)
    }

    fun clear() {
        input.setText("")
    }

    private fun onSendOrStop() {
        if (state.showsStop(text)) {
            actions?.stop()
            return
        }
        if (!state.canSend(text)) return
        performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        )
        actions?.send(text)
        clear()
    }

    private fun renderButton() {
        val stop = state.showsStop(text)
        val enabled = if (stop) !state.stopping else state.canSend(text)
        send.isEnabled = enabled
        val color =
            ContextCompat.getColor(context, if (enabled) R.color.lw_ink_inverted else R.color.lw_ink_faint)
        send.setImageDrawable(
            SymbolDrawable(context, if (stop) "stop" else "arrow_upward", filled = stop, color = color)
        )
        send.contentDescription = context.getString(if (stop) R.string.composer_stop else R.string.composer_send)
    }

    private fun renderFiles() {
        filesScroll.isVisible = state.attachments.isNotEmpty()
        files.removeAllViews()
        val inflater = LayoutInflater.from(context)
        state.attachments.forEach { file ->
            val chip = inflater.inflate(R.layout.view_composer_file, files, false)
            chip.findViewById<TextView>(R.id.composer_file_name).text = file.name
            chip.findViewById<TextView>(R.id.composer_file_meta).text =
                if (file.uploading) "${file.progress.toInt()}%" else file.size
            chip.findViewById<ProgressBar>(R.id.composer_file_progress).apply {
                isVisible = file.uploading
                progress = file.progress.toInt()
            }
            chip.findViewById<ImageButton>(R.id.composer_file_remove).apply {
                setImageDrawable(
                    SymbolDrawable(
                        context,
                        "close",
                        sizeDp = 16f,
                        color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                )
                contentDescription = context.getString(R.string.composer_remove_file, file.name)
                setOnClickListener { actions?.remove(file.id) }
            }
            files.addView(chip)
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
    }
}
