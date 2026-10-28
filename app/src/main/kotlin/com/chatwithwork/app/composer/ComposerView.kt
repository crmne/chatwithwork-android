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
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/**
 * The native composer: "+", the microphone and the web search globe while
 * there's room (ComposerBar), the field, the model, Stop while an answer runs, and Send. It only
 * draws a [ComposerState] and says what the person did ([Actions]); the
 * composer component turns that into replies to the web composer, which keeps
 * every rule. Words stay here until they're sent.
 *
 * While dictation listens and transcribes ([dictate]), its line takes the
 * field's place, since nobody types while dictating, as the web composer's
 * does: "Listening", the waveform across the composer and the time, with
 * Cancel where "+" is and Stop where Send is. The field comes back with the
 * words.
 */
class ComposerView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    interface Actions {
        fun send(text: String)

        fun stop()

        fun openSheet()

        /** The bar's microphone. */
        fun dictate()

        /** The bar's globe: web search [on] or off. */
        fun webSearch(on: Boolean)

        /** The globe while web search can't be switched now: why, from the page ([note]). */
        fun explainWebSearch(note: String?)

        fun chooseModel()

        fun remove(id: String)
    }

    var actions: Actions? = null

    var state: ComposerState = ComposerState()
        private set

    private val lock by lazy { findViewById<TextView>(R.id.composer_lock) }
    private val input by lazy { findViewById<EditText>(R.id.composer_field) }
    private val plus by lazy { findViewById<ImageButton>(R.id.composer_plus) }
    private val dictateButton by lazy { findViewById<ImageButton>(R.id.composer_dictate) }
    private val webSearchButton by lazy { findViewById<ImageButton>(R.id.composer_web_search) }
    private val model by lazy { findViewById<TextView>(R.id.composer_model) }
    private val stop by lazy { findViewById<ImageButton>(R.id.composer_stop) }
    private val send by lazy { findViewById<ImageButton>(R.id.composer_send) }
    private val filesScroll by lazy { findViewById<View>(R.id.composer_files_scroll) }
    private val files by lazy { findViewById<LinearLayout>(R.id.composer_files) }
    private val dictationLine by lazy { findViewById<View>(R.id.composer_dictation) }
    private val dictationStatus by lazy { findViewById<TextView>(R.id.composer_dictation_status) }
    private val dictationDot by lazy { findViewById<View>(R.id.composer_dictation_dot) }
    private val dictationTime by lazy { findViewById<TextView>(R.id.composer_dictation_time) }
    private val waveform by lazy { findViewById<WaveformView>(R.id.composer_waveform) }
    private val dictationCancel by lazy { findViewById<ImageButton>(R.id.composer_dictation_cancel) }
    private val dictationStopFrame by lazy { findViewById<View>(R.id.composer_dictation_stop_frame) }
    private val dictationStop by lazy { findViewById<ImageButton>(R.id.composer_dictation_stop) }
    private val dictationSpinner by lazy { findViewById<View>(R.id.composer_dictation_spinner) }

    /** The dictation running now, listening or transcribing, if any. */
    private var dictation: Dictation.Session? = null

    val dictating: Boolean get() = dictation != null

    // How wide the bar is, in dp, from the last measure; the controls that fit in it follow (ComposerBar).
    private var barWidthDp = 0

    /** What "+" holds now: attaching, and whatever didn't fit in the bar. */
    val sheet: ComposerBar.Sheet get() = ComposerBar.inSheet(state, bar())

    private fun bar(): Set<ComposerBar.Control> = ComposerBar.inBar(barWidthDp, state)

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
        // The bar's width: the composer's, less its gutters and the box's padding.
        val box = width - paddingStart - paddingEnd - (BOX_PADDING_DP * 2 * resources.displayMetrics.density).toInt()
        val barDp = (box / resources.displayMetrics.density).toInt()
        if (barDp != barWidthDp) {
            barWidthDp = barDp
            renderBar()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        plus.setOnClickListener { actions?.openSheet() }
        dictateButton.setImageDrawable(SymbolDrawable(context, "mic", color = muted()))
        dictateButton.setOnClickListener { actions?.dictate() }
        webSearchButton.setOnClickListener {
            val search = state.webSearch ?: return@setOnClickListener
            if (search.disabled) actions?.explainWebSearch(search.note) else actions?.webSearch(!search.on)
        }
        ViewCompat.setAccessibilityDelegate(
            webSearchButton,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = ToggleButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = state.webSearch?.on == true
                }
            }
        )
        model.setOnClickListener { actions?.chooseModel() }
        stop.setOnClickListener { onStop() }
        send.setOnClickListener { onSend() }
        dictationCancel.setImageDrawable(SymbolDrawable(context, "close", color = ink))
        dictationCancel.setOnClickListener { cancelDictation() }
        dictationStop.setOnClickListener { dictation?.stop() }
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
        // A lock (out of credits, a failed answer) ends dictation, unsent, as the web's does.
        if (state.locked && dictation != null) cancelDictation()
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
        model.isVisible = selected != null && !state.locked && !state.working && !dictating
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

        renderBar()
        renderFiles()
        renderButtons()
    }

    private fun muted() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    /**
     * "+", the microphone and the globe: what fits in the bar shows there,
     * the rest waits in "+", which is the paperclip when it only attaches
     * and goes when it holds nothing.
     */
    private fun renderBar() {
        val bar = bar()
        val sheet = sheet
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val on = dictating

        plus.isVisible = !on && !sheet.empty
        plus.setImageDrawable(SymbolDrawable(context, if (sheet.attachOnly) "attach_file" else "add", color = ink))
        plus.contentDescription =
            context.getString(if (sheet.attachOnly) R.string.composer_attach else R.string.composer_add)

        dictateButton.isVisible = !on && ComposerBar.Control.DICTATE in bar
        dictateButton.isEnabled = !state.locked
        dictateButton.alpha = if (state.locked) DISABLED_ALPHA else 1f

        val search = state.webSearch
        webSearchButton.isVisible = !on && search != null && ComposerBar.Control.WEB_SEARCH in bar
        if (search != null) {
            webSearchButton.isSelected = search.on
            webSearchButton.alpha = if (search.disabled) DISABLED_ALPHA else 1f
            webSearchButton.setImageDrawable(
                SymbolDrawable(context, "language", filled = search.on, color = if (search.on) ink else muted())
            )
            ViewCompat.setStateDescription(
                webSearchButton,
                context.getString(if (search.on) R.string.composer_web_search_on else R.string.composer_web_search_off)
            )
            webSearchButton.contentDescription =
                listOfNotNull(
                    context.getString(R.string.composer_web_search_button),
                    search.note?.takeIf { search.disabled && it.isNotBlank() }
                ).joinToString(". ")
        }
    }

    /**
     * Records and transcribes, in the field's place, then hands the words (or
     * why there are none) to [onResult]; false when the microphone couldn't
     * start. [path] is the page's dictation URL.
     */
    fun dictate(path: String, csrfToken: String?, userAgent: String?, onResult: (Dictation.Result) -> Unit): Boolean {
        if (dictation != null) return true
        val scope: CoroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope ?: MainScope()
        val session =
            Dictation.Session(
                context,
                scope,
                path,
                csrfToken,
                userAgent,
                object : Dictation.Listener {
                    override fun onLevel(level: Float, elapsedMs: Long) {
                        waveform.add(level)
                        dictationTime.text = Dictation.elapsed(elapsedMs)
                    }

                    override fun onTranscribing() = renderDictation()

                    override fun onResult(result: Dictation.Result) {
                        dictation = null
                        renderDictation()
                        onResult(result)
                    }
                }
            )
        if (!session.start()) return false
        dictation = session
        waveform.reset()
        dictationTime.text = Dictation.elapsed(0)
        renderDictation()
        return true
    }

    private fun cancelDictation() {
        dictation?.cancel()
        dictation = null
        renderDictation()
    }

    override fun onDetachedFromWindow() {
        // Leaving the page, or the app closing, ends the recording, unsent.
        if (dictation != null) cancelDictation()
        super.onDetachedFromWindow()
    }

    private fun renderDictation() {
        val session = dictation
        val on = session != null
        val transcribing = session?.transcribing == true
        dictationLine.isVisible = on
        input.isVisible = !on
        if (on && input.hasFocus()) {
            input.clearFocus()
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(input.windowToken, 0)
        }
        dictationStatus.setText(if (transcribing) R.string.dictation_transcribing else R.string.dictation_listening)
        dictationStatus.setTextColor(
            ContextCompat.getColor(context, if (transcribing) R.color.lw_ink_muted else R.color.lw_live_ink)
        )
        dictationDot.isVisible = !transcribing
        waveform.frozen = transcribing
        dictationTime.alpha = if (transcribing) TRANSCRIBING_ALPHA else 1f

        renderBar()
        dictationCancel.isVisible = on
        dictationStopFrame.isVisible = on
        dictationStop.isEnabled = !transcribing
        dictationStop.setImageDrawable(
            if (transcribing) {
                null
            } else {
                SymbolDrawable(
                    context,
                    "stop",
                    filled = true,
                    color = ContextCompat.getColor(context, R.color.lw_ink_inverted)
                )
            }
        )
        dictationSpinner.isVisible = transcribing
        model.isVisible = state.selectedModel != null && !state.locked && !state.working && !on
        renderButtons()
        if (!on) input.requestFocus()
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
        val showsSend = state.showsSend(text) && !dictating
        stop.isVisible = state.showsStop() && !dictating
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
        const val TRANSCRIBING_ALPHA = 0.5f

        // The box's padding around the bar (view_composer.xml).
        const val BOX_PADDING_DP = 8

        // Between Stop and Send when both show, the same as between the model chip and Send.
        const val BUTTON_GAP_DP = 8

        // The web conversation's column, 46rem (components/conversation.css): CSS pixels are dp in the web view.
        const val COLUMN_WIDTH_DP = 736
    }
}
