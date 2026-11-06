package com.chatwithwork.app.composer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/**
 * The native composer: "+", the microphone and the web search globe while
 * there's room (ComposerBar), the mode pill unless the mode is Ask first, the
 * field, the model, Stop while an answer runs, and Send. It only
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

        /** The mode pill: the same sheet, showing only Changes. */
        fun openChanges()

        /** The bar's microphone. */
        fun dictate()

        /** The bar's globe: web search [on] or off. */
        fun webSearch(on: Boolean)

        /** The globe while web search can't be switched now: why, from the page ([note]). */
        fun explainWebSearch(note: String?)

        fun chooseModel()

        fun remove(id: String)

        /** Overlay height and web viewport width in the same native units. */
        fun layout(height: Int, width: Int)
    }

    var actions: Actions? = null

    var state: ComposerState = ComposerState()
        private set

    private val lock by lazy { findViewById<TextView>(R.id.composer_lock) }
    private val input by lazy { findViewById<EditText>(R.id.composer_field) }
    private val plus by lazy { findViewById<ImageButton>(R.id.composer_plus) }
    private val dictateButton by lazy { findViewById<ImageButton>(R.id.composer_dictate) }
    private val webSearchButton by lazy { findViewById<ImageButton>(R.id.composer_web_search) }
    private val mode by lazy { findViewById<TextView>(R.id.composer_mode) }
    private val composerBackground = ComposerBackground(context)
    private val composerGlow = ComposerGlow(context)
    private val box by lazy { findViewById<View>(R.id.composer_box) }
    private val boxBounds = Rect()
    private var look = ComposerLook()
    private var glowOpacity = look.glowOpacity
    private var glowFade: ValueAnimator? = null
    private var flow: ValueAnimator? = null
    private val stopRing = StopRing(context)
    private var ringSpin: ValueAnimator? = null
    private val webSearchBadge = WebSearchBadge(context)
    private var onScreen = false
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

    // The screen's lifecycle while a recording runs, watched for the app stopping.
    private var stopWatch: Pair<Lifecycle, DefaultLifecycleObserver>? = null

    val dictating: Boolean get() = dictation != null

    // How wide the bar is, in dp, from the last measure; the controls that fit in it follow (ComposerBar).
    private var barWidthDp = 0

    // How wide the mode pill measures, in dp, 0 without one; the bar makes room for it.
    private var modeWidthDp = 0

    /** What "+" holds now: attaching, whatever didn't fit in the bar, and the modes. */
    val sheet: ComposerBar.Sheet get() = ComposerBar.inSheet(state, bar())

    private fun bar(): Set<ComposerBar.Control> = ComposerBar.inBar(barWidthDp, state, modeDp = modeWidthDp)

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
        box.background = composerBackground
        input.setOnFocusChangeListener { _, _ -> updateLook() }
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        plus.setOnClickListener { actions?.openSheet() }
        mode.setOnClickListener { actions?.openChanges() }
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
        stop.foreground = stopRing
        send.setOnClickListener { onSend() }
        dictationCancel.setImageDrawable(SymbolDrawable(context, "close", sizeDp = ICON_DP, color = ink))
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
        updateLook()
        // A new composer may already hold words (shared in, or a draft): the field starts with them.
        if (connecting && text.isBlank() && !state.text.isNullOrBlank()) {
            input.setText(state.text)
            input.setSelection(input.length())
        }
        isVisible = state.visible
        if (!state.visible) input.clearFocus()
        // A lock (out of credits, a failed answer) ends dictation, unsent, as the web's does.
        if (state.locked && dictation != null) cancelDictation()
        input.hint = state.placeholder.ifBlank { context.getString(R.string.composer_placeholder) }
        input.isEnabled = !state.locked

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
        model.contentDescription = selected?.let { context.getString(R.string.composer_model_description, it.name) }
        model.setCompoundDrawablesRelative(
            selected?.let { ModelLogo.drawable(context, it, 16) },
            null,
            SymbolDrawable(context, "unfold_more", sizeDp = 16f, color = model.currentTextColor).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            },
            null
        )

        renderBar()
        renderFiles()
        renderButtons()
        post { reportLayout() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) reportLayout()
    }

    private fun reportLayout() {
        val container = parent as? View ?: return
        val page = container.findViewById<View>(R.id.composer_page) ?: return
        // Servers without overlay support cannot reserve scroll space themselves.
        val inset = if (state.overlay || !state.visible) 0 else height
        val params = page.layoutParams as FrameLayout.LayoutParams
        if (params.bottomMargin != inset) page.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = inset }
        if (state.overlay && container.width > 0) actions?.layout(if (state.visible) height else 0, container.width)
    }

    private fun updateLook() {
        val wanted = ComposerLook.of(state, input.hasFocus())
        if (wanted == look) return
        val edgeChanged = wanted.edge != look.edge
        look = wanted
        composerBackground.look = wanted
        fadeGlow(wanted.glowOpacity)
        if (edgeChanged) {
            // The amber or rainbow bitmap, and the shadow's place, are drawn here.
            invalidate()
            updateFlow()
        }
    }

    /** With the web's --duration-base. Animations off, the animator lands at once. */
    private fun fadeGlow(opacity: Float) {
        glowFade?.cancel()
        if (opacity == glowOpacity) return
        glowFade = ValueAnimator.ofFloat(glowOpacity, opacity).apply {
            duration = LOOK_FADE_MS
            addUpdateListener {
                glowOpacity = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * The edge flows on screen while an answer runs, one loop in 5 seconds, and
     * stands still with animations off, the system's reduced motion.
     */
    private fun updateFlow() {
        val flowing = look.edge == ComposerLook.Edge.FLOWING && onScreen && ValueAnimator.areAnimatorsEnabled()
        if (!flowing) {
            flow?.cancel()
            flow = null
            composerBackground.flow = 0f
        } else if (flow == null) {
            flow = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = FLOW_MS
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { composerBackground.flow = it.animatedFraction }
                start()
            }
        }
    }

    /**
     * Stop's rainbow turns while it shows on screen, once in 2.4 seconds, and
     * once in 6 while the answer stops (the web's .chat-prompt__ring). With
     * animations off, the system's reduced motion, it stands still.
     */
    private fun updateRing() {
        val spinning = stop.isVisible && onScreen && ValueAnimator.areAnimatorsEnabled()
        val duration = if (stopRing.stopping) RING_STOPPING_MS else RING_MS
        if (!spinning) {
            ringSpin?.cancel()
            ringSpin = null
            stopRing.turn = 0f
        } else if (ringSpin?.duration != duration) {
            val from = stopRing.turn
            ringSpin?.cancel()
            ringSpin = ValueAnimator.ofFloat(from, from + 1f).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { stopRing.turn = (it.animatedValue as Float) % 1f }
                start()
            }
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        onScreen = isVisible
        updateFlow()
        updateRing()
    }

    // Behind every child, and past this view's bounds: see onAttachedToWindow.
    override fun dispatchDraw(canvas: Canvas) {
        if (box.isVisible) {
            box.getHitRect(boxBounds)
            composerGlow.draw(canvas, boxBounds, look, glowOpacity)
        }
        super.dispatchDraw(canvas)
    }

    private fun muted() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    /**
     * "+", the microphone, the globe and the mode pill: what fits in the bar
     * shows there, and the rest waits in "+", which is always there, disabled
     * while locked or holding nothing.
     */
    private fun renderBar() {
        renderMode()
        val bar = bar()
        val sheet = sheet
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val on = dictating

        plus.isVisible = !on
        plus.isEnabled = !state.locked && !sheet.empty
        // A locked button dims once, as the web's disabled ones do: ink at 20% in the icon, and no opacity on top.
        val dimmed = ContextCompat.getColor(context, R.color.composer_stop_disabled_icon)
        val buttonInk = if (state.locked) dimmed else ink
        plus.setImageDrawable(
            SymbolDrawable(context, "add", sizeDp = ICON_DP, color = if (plus.isEnabled) ink else dimmed)
        )
        // While web search is on and its switch waits in "+", "+" carries the web's small globe.
        val searching = state.webSearch?.let { it.on && !it.disabled } == true
        webSearchBadge.rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        plus.foreground = webSearchBadge.takeIf { searching && ComposerBar.Control.WEB_SEARCH in sheet.folded }

        dictateButton.isVisible = !on && ComposerBar.Control.DICTATE in bar
        dictateButton.isEnabled = !state.locked
        dictateButton.setImageDrawable(SymbolDrawable(context, "mic", sizeDp = ICON_DP, color = buttonInk))

        val search = state.webSearch
        webSearchButton.isVisible = !on && search != null && ComposerBar.Control.WEB_SEARCH in bar
        if (search != null) {
            // The web's globe (.chat-prompt__web-search-toggle): Phosphor's, muted on nothing when off, ink on a
            // pressed square when on (composer_toggle_button), and ink at 20% on nothing when it can't be switched,
            // as the web's disabled button (measured while an answer runs).
            webSearchButton.isSelected = search.on && !search.disabled
            webSearchButton.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_globe)?.mutate()?.apply {
                    setTint(
                        when {
                            search.disabled -> ContextCompat.getColor(context, R.color.composer_stop_disabled_icon)
                            search.on -> ink
                            else -> muted()
                        }
                    )
                }
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

        val buttons = listOf(plus, dictateButton, webSearchButton).count { it.isVisible }
        val density = resources.displayMetrics.density
        val modelMaxDp = ComposerBar.modelMaxWidthDp(barWidthDp, buttons, modeWidthDp)
        model.maxWidth = (modelMaxDp * density).toInt()
        val modelChromeDp = ((model.compoundPaddingStart + model.compoundPaddingEnd) / density).toInt()
        // Without a logo to say which model it is, its name stays, cut short if it must.
        val hasLogo = model.compoundDrawablesRelative[0] != null
        model.text =
            state.selectedModel?.name?.takeIf { !hasLogo || ComposerBar.modelNameFits(modelMaxDp, modelChromeDp) }
    }

    /**
     * The pill says the mode isn't Ask first, on the bar and from the server's
     * value alone: Allow everything is hard to miss, in the negative tint, and
     * a mode this build doesn't know shows the server's own label.
     */
    private fun renderMode() {
        val selected = ComposerBar.selectedMode(state)
        mode.isVisible = selected != null && !dictating
        if (selected == null) {
            modeWidthDp = 0
            return
        }
        val everything = selected.value == MODE_EVERYTHING
        val color = ContextCompat.getColor(context, if (everything) R.color.lw_negative_ink else R.color.lw_ink_muted)
        val symbol = when (selected.value) {
            MODE_EDITS -> "edit"
            MODE_EVERYTHING -> "report"
            else -> "tune"
        }
        mode.text = when (selected.value) {
            MODE_EDITS -> context.getString(R.string.composer_mode_edits)
            MODE_EVERYTHING -> context.getString(R.string.composer_mode_everything)
            else -> selected.label
        }
        mode.setTextColor(color)
        // A new background can reset the view's padding to the drawable's own, its top and bottom insets.
        val paddingStart = mode.paddingStart
        val paddingEnd = mode.paddingEnd
        mode.setBackgroundResource(if (everything) R.drawable.composer_mode_everything else R.drawable.composer_mode)
        mode.setPaddingRelative(paddingStart, 0, paddingEnd, 0)
        mode.setCompoundDrawablesRelative(
            SymbolDrawable(context, symbol, sizeDp = MODE_ICON_DP, color = color).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            },
            null,
            null,
            null
        )
        mode.contentDescription = context.getString(R.string.composer_mode_description, selected.label)
        mode.isEnabled = !state.locked
        mode.alpha = if (state.locked) MODE_LOCKED_ALPHA else 1f

        val density = resources.displayMetrics.density
        mode.maxWidth = (ComposerBar.modeMaxWidthDp(barWidthDp) * density).toInt()
        mode.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        modeWidthDp = ceil(mode.measuredWidth / density).toInt()
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
                    // A phone that dims and locks mid-sentence stops the app, which ends the recording unsent, so
                    // the screen stays on while the microphone is open. Android lets go of it by itself too, once
                    // this view leaves the window.
                    override fun onListening(listening: Boolean) {
                        keepScreenOn = listening
                    }

                    override fun onLevel(level: Float, elapsedMs: Long) {
                        waveform.add(level)
                        dictationTime.text = Dictation.elapsed(elapsedMs)
                    }

                    override fun onTranscribing() = renderDictation()

                    override fun onResult(result: Dictation.Result) {
                        unwatchStop()
                        dictation = null
                        renderDictation()
                        onResult(result)
                    }
                }
            )
        if (!session.start()) return false
        dictation = session
        watchStop(findViewTreeLifecycleOwner()?.lifecycle)
        waveform.reset()
        dictationTime.text = Dictation.elapsed(0)
        renderDictation()
        return true
    }

    /**
     * Going to the background or locking the phone ends a recording, unsent:
     * Android gives a stopped app no microphone, so it would only record
     * silence. Words already sent to be transcribed still come back.
     */
    private fun watchStop(lifecycle: Lifecycle?) {
        unwatchStop()
        if (lifecycle == null) return
        val observer =
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (dictation?.transcribing == false) cancelDictation()
                }
            }
        lifecycle.addObserver(observer)
        stopWatch = lifecycle to observer
    }

    private fun unwatchStop() {
        stopWatch?.let { (lifecycle, observer) -> lifecycle.removeObserver(observer) }
        stopWatch = null
    }

    private fun cancelDictation() {
        unwatchStop()
        dictation?.cancel()
        dictation = null
        renderDictation()
    }

    /**
     * The glow reaches past this view, into the gesture bar's strip, so nothing
     * above it may clip: the window's padding for that strip, and the navigation
     * host's own container, which is made in code and out of a layout's reach.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        generateSequence(parent as? ViewGroup) { it.parent as? ViewGroup }
            .takeWhile { it.id != android.R.id.content }
            .forEach {
                it.clipChildren = false
                it.clipToPadding = false
            }
    }

    override fun onDetachedFromWindow() {
        onScreen = false
        updateFlow()
        updateRing()
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
        // As the web's microphone: live while it listens, a ghost with a spinner while the words come back.
        dictationStop.setBackgroundResource(
            if (transcribing) R.drawable.composer_ghost_button else R.drawable.composer_listening_button
        )
        dictationStop.setImageDrawable(
            if (transcribing) {
                null
            } else {
                SymbolDrawable(
                    context,
                    "stop",
                    sizeDp = STOP_ICON_DP,
                    filled = true,
                    color = ContextCompat.getColor(context, R.color.lw_live_ink)
                )
            }
        )
        dictationSpinner.isVisible = transcribing
        model.isVisible = state.selectedModel != null && !state.locked && !state.working && !on
        renderButtons()
        if (!on) input.requestFocus()
    }

    /**
     * Puts the caret in the field and brings up the keyboard, unless the
     * composer is hidden or locked. Android drops a request made while the
     * window settles (the splash leaving on a cold start), so it asks again,
     * a few times, until the keyboard shows or the field has lost the focus.
     */
    fun focusField(attempts: Int = KEYBOARD_ATTEMPTS) {
        if (!isAttachedToWindow || !isVisible || !input.isEnabled) return
        val keyboard = WindowInsetsCompat.Type.ime()
        input.requestFocus()
        ViewCompat.getWindowInsetsController(input)?.show(keyboard)
        if (attempts <= 1) return
        postDelayed({
            val shown = ViewCompat.getRootWindowInsets(input)?.isVisible(keyboard) == true
            if (!shown && input.hasFocus()) focusField(attempts - 1)
        }, KEYBOARD_RETRY_MS)
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

    // As the web's: while an answer runs, Stop is the ink square at the bar's end, and once there are words or files
    // Send joins it on its left as the subtle one (chats/_submit_button). Both sit flush, as the touch bar's do.
    private fun renderButtons() {
        val showsSend = state.showsSend(text) && !dictating
        val showsStop = state.showsStop() && !dictating
        stop.isVisible = showsStop
        send.isVisible = showsSend

        stop.isEnabled = state.canStop
        stopRing.stopping = !state.canStop
        updateRing()
        val stopColor = if (stop.isEnabled) R.color.lw_ink_inverted else R.color.composer_stop_disabled_icon
        stop.setImageDrawable(
            ContextCompat.getDrawable(context, R.drawable.ic_stop_fill)?.mutate()?.apply {
                setTint(ContextCompat.getColor(context, stopColor))
            }
        )

        send.isEnabled = state.canSend(text)
        send.setBackgroundResource(
            if (showsStop) R.drawable.composer_subtle_button else R.drawable.composer_send_background
        )
        val sendColor =
            when {
                !send.isEnabled -> R.color.composer_send_disabled_icon
                showsStop -> R.color.lw_ink
                else -> R.color.lw_ink_inverted
            }
        send.setImageDrawable(
            SymbolDrawable(
                context,
                "arrow_upward",
                sizeDp = ICON_DP,
                color = ContextCompat.getColor(context, sendColor)
            )
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
        const val TRANSCRIBING_ALPHA = 0.5f
        const val KEYBOARD_ATTEMPTS = 8
        const val KEYBOARD_RETRY_MS = 400L

        // The server's values for the modes the pill has a look and a short label for.
        const val MODE_EDITS = "edits"
        const val MODE_EVERYTHING = "everything"
        const val MODE_ICON_DP = 16f

        // The pill keeps its words readable while dimmed, so it says which mode a locked chat holds.
        const val MODE_LOCKED_ALPHA = 0.4f

        // --duration-base, and rainbow-flow's 5s.
        const val LOOK_FADE_MS = 220L
        const val FLOW_MS = 5000L

        // .chat-prompt__ring's spin, and its slower one while the answer stops.
        const val RING_MS = 2400L
        const val RING_STOPPING_MS = 6000L

        // The box's padding around the bar (view_composer.xml).
        const val BOX_PADDING_DP = 8

        // The web's touch bar draws Phosphor icons at 20px; Material Symbols leave more room around their glyphs, so
        // 24dp draws them as large (the microphone measured 16.4dp tall in both). An answer's Stop is Phosphor's own
        // (ic_stop_fill); this size is dictation's Stop, which the web has no glyph for: its microphone stays.
        const val ICON_DP = 24f
        const val STOP_ICON_DP = 18f

        // The web conversation's column, 46rem (components/conversation.css): CSS pixels are dp in the web view.
        const val COLUMN_WIDTH_DP = 736
    }
}
