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
import androidx.core.view.updateLayoutParams
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors

/**
 * The native composer: "+", the field, the model, Stop while an answer runs, and Send. It only
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
    private val stop by lazy { findViewById<ImageButton>(R.id.composer_stop) }
    private val send by lazy { findViewById<ImageButton>(R.id.composer_send) }
    private val filesScroll by lazy { findViewById<View>(R.id.composer_files_scroll) }
    private val files by lazy { findViewById<LinearLayout>(R.id.composer_files) }

    val text: String get() = input.text?.toString().orEmpty()

    // The gutter the layout gives the composer, kept on phones; on a tablet the composer centres under the page's
    // column instead of spanning the screen.
    private var gutterStart = -1
    private var gutterEnd = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (gutterStart < 0) {
            gutterStart = paddingStart
            gutterEnd = paddingEnd
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val widest = (COLUMN_WIDTH_DP * resources.displayMetrics.density).toInt() + gutterStart + gutterEnd
        val extra = ((width - widest) / 2).coerceAtLeast(0)
        if (paddingStart != gutterStart + extra || paddingEnd != gutterEnd + extra) {
            setPaddingRelative(gutterStart + extra, paddingTop, gutterEnd + extra, paddingBottom)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        plus.setImageDrawable(SymbolDrawable(context, "add", color = ink))
        plus.setOnClickListener { actions?.openSheet() }
        model.setOnClickListener { actions?.chooseModel() }
        stop.setOnClickListener { onStop() }
        send.setOnClickListener { onSend() }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) = renderButtons()
            }
        )
    }

    fun show(state: ComposerState, connecting: Boolean = false) {
        this.state = state
        // A new composer may already hold words (shared in, or a draft): the field starts with them.
        if (connecting && text.isBlank() && !state.text.isNullOrBlank()) {
            input.setText(state.text)
            input.setSelection(input.length())
        }
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
        renderButtons()
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

    private fun onStop() {
        if (state.canStop) actions?.stop()
    }

    private fun onSend() {
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

    // While an answer runs, Stop stands alone in ink until there are words or files; then Send joins it as the ink
    // button and Stop steps back to an outline beside it.
    private fun renderButtons() {
        val showsSend = state.showsSend(text)
        stop.isVisible = state.showsStop()
        send.isVisible = showsSend

        val stopAlone = !showsSend
        stop.isEnabled = state.canStop
        stop.setBackgroundResource(
            if (stopAlone) R.drawable.composer_send_background else R.drawable.composer_stop_quiet_background
        )
        val stopColor =
            when {
                !stop.isEnabled -> R.color.lw_ink_faint
                stopAlone -> R.color.lw_ink_inverted
                else -> R.color.lw_ink
            }
        stop.setImageDrawable(
            SymbolDrawable(context, "stop", filled = true, color = ContextCompat.getColor(context, stopColor))
        )
        val gap = if (showsSend) (BUTTON_GAP_DP * resources.displayMetrics.density).toInt() else 0
        if ((stop.layoutParams as MarginLayoutParams).marginEnd != gap) {
            stop.updateLayoutParams<MarginLayoutParams> { marginEnd = gap }
        }

        send.isEnabled = state.canSend(text)
        val sendColor = if (send.isEnabled) R.color.lw_ink_inverted else R.color.lw_ink_faint
        send.setImageDrawable(
            SymbolDrawable(context, "arrow_upward", color = ContextCompat.getColor(context, sendColor))
        )
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

        // Between Stop and Send when both show, the same as between the model chip and Send.
        const val BUTTON_GAP_DP = 8

        // The web conversation's column, 46rem (components/conversation.css): CSS pixels are dp in the web view.
        const val COLUMN_WIDTH_DP = 736
    }
}
