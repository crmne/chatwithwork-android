package com.chatwithwork.app.fragments

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.AttrRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chatwithwork.app.R

/**
 * Some of a chat's bones (ChatSkeleton), filled as one shape with one shimmer
 * laid over the lot. Its parts (ChatSkeletonOverlay) share their coordinates,
 * so one band crosses them all. It takes no touches: Hotwire Native's progress
 * container under it holds them back from the page until the page has rendered.
 */
class ChatSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    /** Rounded evenly, which draws far cheaper than a path, frame after frame. */
    private var rounded: List<Pair<RectF, Float>> = emptyList()

    /** What isn't: the question bubbles, with their tails. */
    private val path = Path()
    private val bonePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.lw_surface_selected) }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandMatrix = Matrix()
    private var bandShader: Shader? = null

    /** Where the shimmer's band starts, or null while it isn't moving. */
    private var bandX: Float? = null

    /** The size of the area the band crosses, the screen's, whichever view this is. */
    private var sweepWidth = 0f
    private var sweepHeight = 0f

    fun setBones(bones: List<ChatSkeleton.Bone>) {
        val (even, uneven) = bones.partition { it.radii.distinct().size == 1 }
        rounded = even.map { RectF(it.left, it.top, it.right, it.bottom) to it.radii.first() }
        path.rewind()
        uneven.forEach { bone ->
            val corners = bone.radii.flatMap { listOf(it, it) }.toFloatArray()
            path.addRoundRect(RectF(bone.left, bone.top, bone.right, bone.bottom), corners, Path.Direction.CW)
        }
        invalidate()
    }

    /** Places the band for this frame; null leaves the bones still. */
    fun setBand(x: Float?, width: Float, height: Float) {
        if (width != sweepWidth || height != sweepHeight) {
            sweepWidth = width
            sweepHeight = height
            bandShader = band(width)
            bandPaint.shader = bandShader
        }
        bandX = x
        if (isVisible) invalidate()
    }

    private fun band(width: Float): Shader {
        val highlight = ContextCompat.getColor(context, R.color.skeleton_highlight)
        val length = ChatSkeleton.bandWidth(width)
        // Leaning across the screen: its lines of equal color run ChatSkeleton.BAND_SLANT sideways per pixel down.
        val across = length / (1 + ChatSkeleton.BAND_SLANT * ChatSkeleton.BAND_SLANT)
        return LinearGradient(
            0f,
            0f,
            across,
            across * ChatSkeleton.BAND_SLANT,
            intArrayOf(Color.TRANSPARENT, highlight, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        drawBones(canvas, bonePaint)
        val x = bandX ?: return
        bandMatrix.setTranslate(x, 0f)
        bandShader?.setLocalMatrix(bandMatrix)
        drawBones(canvas, bandPaint)
    }

    private fun drawBones(canvas: Canvas, paint: Paint) {
        rounded.forEach { (rect, radius) -> canvas.drawRoundRect(rect, radius, radius, paint) }
        canvas.drawPath(path, paint)
    }
}

/**
 * A chat's skeleton on its screen (fragment_web), in three parts. The body
 * covers the page until it paints, then crossfades to it. The title's bone
 * waits in the bar until the page names the chat. The composer's bone waits
 * for the real composer, which comes with the page's scripts, a while after
 * its first paint. All of it goes for an error, and by itself after
 * ChatSkeleton.TIMEOUT_MS whatever happens.
 */
class ChatSkeletonOverlay(root: View, private val toolbar: Toolbar, private val bar: View) {
    private val body: ChatSkeletonView = root.findViewById(R.id.skeleton_body)
    private val composer: ChatSkeletonView = root.findViewById(R.id.skeleton_composer)
    private val title: ChatSkeletonView = root.findViewById(R.id.skeleton_title)
    private val parts = listOf(body, composer, title)

    /** Hidden from screen readers while the body stands in for it: it may still hold the last page. */
    private val page: View = root.findViewById(R.id.page_area)
    private val rtl get() = body.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private var sweep: ValueAnimator? = null
    private val timeout = Runnable { settle() }
    private val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }

    /** The body still covers the page. */
    val covering: Boolean
        get() = body.isVisible && body.alpha == 1f

    /** A chat's screen appeared. */
    fun show() {
        parts.forEach {
            it.alpha = 1f
            it.isVisible = true
        }
        page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        body.addOnLayoutChangeListener(relayout)
        bar.addOnLayoutChangeListener(relayout)
        body.postDelayed(timeout, ChatSkeleton.TIMEOUT_MS)
        if (ChatSkeleton.sweeps(ValueAnimator.areAnimatorsEnabled())) startSweep()
    }

    /** The page named the chat: the bar shows that instead of the title's bone. */
    fun titleKnown() {
        title.isVisible = false
    }

    /** The page's first paint: the body crossfades to it. */
    fun pagePainted() {
        if (!covering) return
        page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        fadeOut(body)
    }

    /** The page has rendered, so its composer is coming: all that's left crossfades away. */
    fun settle() {
        pagePainted()
        fadeOut(composer) { stop() }
        fadeOut(title)
    }

    /** An error view, or the screen going away: no fade over what comes next. */
    fun remove() {
        page.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        parts.forEach {
            it.animate().cancel()
            it.isVisible = false
        }
        stop()
    }

    private fun fadeOut(part: ChatSkeletonView, then: () -> Unit = {}) {
        if (!part.isVisible) {
            then()
            return
        }
        part
            .animate()
            .alpha(0f)
            .setDuration(ChatSkeleton.FADE_MS)
            .withEndAction {
                part.isVisible = false
                then()
            }
    }

    private fun stop() {
        sweep?.cancel()
        sweep = null
        body.removeCallbacks(timeout)
        body.removeOnLayoutChangeListener(relayout)
        bar.removeOnLayoutChangeListener(relayout)
    }

    private fun place() {
        val width = body.width.toFloat()
        val height = body.height.toFloat()
        val density = body.resources.displayMetrics.density
        val bones = ChatSkeleton.body(width, height, bar.bottom.toFloat(), density, rtl)
        // The composer's bone is the last, and lasts longest.
        body.setBones(bones.dropLast(1))
        composer.setBones(bones.takeLast(1))
        // As the toolbar places its title: past its content inset and the navigation button, then its margin.
        val inset = if (rtl) toolbar.currentContentInsetRight else toolbar.currentContentInsetLeft
        val start = maxOf(inset.toFloat(), NAVIGATION_BUTTON_DP * density) + toolbar.titleMarginStart
        val centerY = bar.top + toolbar.top + toolbar.height / 2f
        title.setBones(listOf(ChatSkeleton.title(width, start, centerY, density, rtl)))
    }

    private fun startSweep() {
        sweep =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ChatSkeleton.SWEEP_MS
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val width = body.width.toFloat()
                    val height = body.height.toFloat()
                    if (width == 0f) return@addUpdateListener
                    val x = ChatSkeleton.bandOffset(animator.animatedFraction, width, height, rtl)
                    parts.forEach { it.setBand(x, width, height) }
                }
                start()
            }
    }
}

/** The toolbar's navigation button, the sidebar's on a chat. */
private const val NAVIGATION_BUTTON_DP = 48f
