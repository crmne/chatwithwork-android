package com.chatwithwork.app.fragments

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chatwithwork.app.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A modal's frame, Material 3's full-screen dialog: the screen it came up
 * over (a still picture of it, [ModalBackdrop], since the navigator's screen
 * under a modal has no view of its own), a scrim, and the modal's page on its
 * card ([card]).
 *
 * - In a compact window (a phone, or any window less than 600dp wide or
 *   480dp tall) the card fills the frame, as a full-screen dialog does; the
 *   picture and the scrim show only while a predictive back gesture shrinks
 *   the card.
 * - In a medium or expanded one (a tablet, an unfolded phone) the card is a
 *   dialog: centred, at most [MAX_WIDTH_DP] wide, with Material 3's
 *   extra-large corners, over the scrim. Tapping the scrim closes it, as it
 *   closes a basic dialog.
 *
 * It slides up as it opens and down as it closes (full-screen), or fades and
 * grows in and out (dialog), with Material 3's emphasized easing, and follows
 * a predictive back gesture by shrinking toward the edge it started from.
 */
@SuppressLint("ViewConstructor")
class ModalFrame(context: Context, page: View, private val onScrimTapped: () -> Unit) : FrameLayout(context) {
    /** Whether the card is a dialog (a large window) rather than the whole screen. */
    val dialog: Boolean = isDialog(context.resources.configuration)

    private val density = context.resources.displayMetrics.density
    private val margin = MARGIN_DP * density
    private val dialogCorner = CORNER_DP * density

    private val backdrop =
        ImageView(context).apply {
            // At its own scale from the top: with the keyboard up the frame is shorter, and the picture's bottom goes under it.
            scaleType = ImageView.ScaleType.MATRIX
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            showIf(false)
        }

    private val scrim =
        View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.scrim))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = if (dialog) 1f else 0f
            if (dialog) setOnClickListener { onScrimTapped() }
        }

    /** The modal's page, on its card. */
    val card: FrameLayout =
        FrameLayout(context).apply {
            // Touches on the card stay there, never reaching the scrim under it.
            isClickable = true
            clipToOutline = true
            outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, corner)
                    }
                }
            if (dialog) elevation = DIALOG_ELEVATION_DP * density
            addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

    private var corner: Float = if (dialog) dialogCorner else 0f
        set(value) {
            field = value
            card.invalidateOutline()
        }

    /** The status bar's height: a dialog keeps below it, a full-screen card's own bar pads for it. */
    private var topInset = 0

    private var settling: ValueAnimator? = null
    private var gestureStartY = 0f

    init {
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (topInset != bars.top) {
                topInset = bars.top
                requestLayout()
            }
            // A dialog sits clear of the system bars: its page's own bar has no status bar to pad for.
            val forCard =
                if (dialog) {
                    WindowInsetsCompat.Builder(insets)
                        .setInsets(
                            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                            Insets.NONE
                        )
                        .build()
                } else {
                    insets
                }
            ViewCompat.dispatchApplyWindowInsets(card, forCard)
            WindowInsetsCompat.CONSUMED
        }
        ModalBackdrop.observe(this) { picture -> showBackdrop(picture) }
    }

    private fun View.showIf(visible: Boolean) {
        visibility = if (visible) VISIBLE else INVISIBLE
    }

    private var picture: Bitmap? = null

    private fun showBackdrop(picture: Bitmap?) {
        this.picture = picture
        backdrop.setImageBitmap(picture)
        updateBackdrop()
    }

    /**
     * The picture only where it's of this frame's width and at least its
     * height (the keyboard only takes from the bottom): after a rotation it's
     * of another screen, and the canvas shows instead.
     */
    private fun updateBackdrop() {
        val picture = picture
        val fits = picture != null && width > 0 && abs(picture.width - width) <= 2 && picture.height >= height - 2
        backdrop.showIf(fits)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val (cardWidth, cardHeight) = if (dialog) dialogSize(width, height) else width to height
        (card.layoutParams as LayoutParams).apply {
            this.width = cardWidth
            this.height = cardHeight
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dialogSize(width: Int, height: Int): Pair<Int, Int> {
        val cardWidth = min((MAX_WIDTH_DP * density).toInt(), (width - 2 * margin).toInt())
        val cardHeight = min((MAX_HEIGHT_DP * density).toInt(), (height - topInset - 2 * margin).toInt())
        return max(cardWidth, 0) to max(cardHeight, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (dialog) {
            // Centred in what the status bar leaves (the keyboard and the gesture bar are the activity's padding).
            val x = (width - card.measuredWidth) / 2
            val y = topInset + (height - topInset - card.measuredHeight) / 2
            card.layout(x, y, x + card.measuredWidth, y + card.measuredHeight)
        }
        updateBackdrop()
    }

    // region Opening and closing

    /** Slides up (full-screen), or fades and grows in (dialog). */
    fun animateIn() {
        settling?.cancel()
        if (dialog) {
            scrim.alpha = 0f
            card.alpha = 0f
            card.scaleX = DIALOG_START_SCALE
            card.scaleY = DIALOG_START_SCALE
            scrim.animate().alpha(1f).setDuration(DIALOG_IN_MS).setInterpolator(EMPHASIZED_DECELERATE).start()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(DIALOG_IN_MS).setInterpolator(EMPHASIZED_DECELERATE).start()
        } else {
            card.translationY = (if (height > 0) height else resources.displayMetrics.heightPixels).toFloat()
            card.animate().translationY(0f).setDuration(ENTER_MS).setInterpolator(EMPHASIZED_DECELERATE).start()
        }
    }

    /** Slides down (full-screen), or fades and shrinks away (dialog), from wherever a back gesture left it. */
    fun animateOut() {
        settling?.cancel()
        scrim.animate().alpha(0f).setDuration(if (dialog) DIALOG_OUT_MS else EXIT_MS).start()
        if (dialog) {
            card.animate().alpha(0f).scaleX(card.scaleX * DIALOG_END_SCALE).scaleY(card.scaleY * DIALOG_END_SCALE)
                .setDuration(DIALOG_OUT_MS).setInterpolator(EMPHASIZED_ACCELERATE).start()
        } else {
            card.animate().translationY(
                height.toFloat()
            ).setDuration(EXIT_MS).setInterpolator(EMPHASIZED_ACCELERATE).start()
        }
    }

    // endregion

    // region Predictive back

    fun backStarted(event: BackEventCompat) {
        settling?.cancel()
        card.animate().cancel()
        gestureStartY = event.touchY
    }

    /**
     * Material 3's predictive back for a full-screen surface: the card shrinks
     * to 90%, leans away from the edge the swipe started at and a little with
     * the finger, and rounds its corners, showing the screen it came up over.
     */
    fun backProgressed(event: BackEventCompat) {
        val progress = event.progress
        val scale = 1f - (1f - BACK_SCALE) * progress
        card.scaleX = scale
        card.scaleY = scale
        val lean = max(0f, card.width / BACK_LEAN_DIVISOR - BACK_LEAN_MARGIN_DP * density)
        card.translationX = progress * lean * if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        val drift = if (card.height > 0) ((event.touchY - gestureStartY) / card.height).coerceIn(-1f, 1f) else 0f
        card.translationY = drift * card.height * BACK_DRIFT
        if (!dialog) {
            corner = dialogCorner * min(1f, progress * BACK_CORNER_SPEED)
            scrim.alpha = progress
        }
    }

    /** The gesture let go short of going back: everything as it was. */
    fun backCancelled() {
        val from = BackState(card.scaleX, card.translationX, card.translationY, corner, scrim.alpha)
        val to = BackState(1f, 0f, 0f, if (dialog) dialogCorner else 0f, if (dialog) 1f else 0f)
        settling?.cancel()
        settling =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = BACK_CANCEL_MS
                interpolator = EMPHASIZED_DECELERATE
                addUpdateListener {
                    val t = it.animatedFraction
                    val scale = lerp(from.scale, to.scale, t)
                    card.scaleX = scale
                    card.scaleY = scale
                    card.translationX = lerp(from.x, to.x, t)
                    card.translationY = lerp(from.y, to.y, t)
                    corner = lerp(from.corner, to.corner, t)
                    scrim.alpha = lerp(from.scrim, to.scrim, t)
                }
                start()
            }
    }

    private data class BackState(val scale: Float, val x: Float, val y: Float, val corner: Float, val scrim: Float)

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    // endregion

    override fun onDetachedFromWindow() {
        settling?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** Material 3's window size classes: medium from 600dp wide; a phone on its side (under 480dp tall) stays compact. */
        const val MEDIUM_WIDTH_DP = 600
        const val COMPACT_HEIGHT_DP = 480

        /** Material 3: a dialog at most 560dp wide; tall enough here for a page and its composer. */
        const val MAX_WIDTH_DP = 560
        const val MAX_HEIGHT_DP = 840
        const val MARGIN_DP = 24
        const val CORNER_DP = 28f
        private const val DIALOG_ELEVATION_DP = 6f

        /** Material 3's emphasized easing: decelerate to enter, accelerate to leave. */
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

        /** Material 3's durations: long2 to slide in, short4 to slide out; medium2 and short3 for a dialog. */
        const val ENTER_MS = 500L
        const val EXIT_MS = 200L
        const val DIALOG_IN_MS = 300L
        const val DIALOG_OUT_MS = 150L
        private const val DIALOG_START_SCALE = 0.9f
        private const val DIALOG_END_SCALE = 0.95f

        private const val BACK_SCALE = 0.9f
        private const val BACK_LEAN_DIVISOR = 20f
        private const val BACK_LEAN_MARGIN_DP = 8f
        private const val BACK_DRIFT = 0.05f
        private const val BACK_CORNER_SPEED = 4f
        private const val BACK_CANCEL_MS = 250L

        /** Whether a modal shows as a dialog in [configuration]'s window rather than over the whole of it. */
        fun isDialog(configuration: Configuration): Boolean =
            isDialog(configuration.screenWidthDp, configuration.screenHeightDp)

        fun isDialog(widthDp: Int, heightDp: Int): Boolean = widthDp >= MEDIUM_WIDTH_DP && heightDp >= COMPACT_HEIGHT_DP
    }
}
