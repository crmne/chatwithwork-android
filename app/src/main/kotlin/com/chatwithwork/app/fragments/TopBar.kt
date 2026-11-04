package com.chatwithwork.app.fragments

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import android.webkit.WebView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chatwithwork.app.R
import dev.hotwire.navigation.R as HotwireR
import java.lang.ref.WeakReference

/**
 * Every screen's top bar (fragment_web): no surface, no hairline, one
 * compact row whose title and buttons sit straight on the page, and under
 * them a fade that eases what scrolls up into the canvas. Chats, pages,
 * and modals all draw it, through this one class.
 *
 * Where the page meets it depends on whether the page keeps clear of it:
 * - [under]: the page runs up under the bar and leaves room for it itself,
 *   told how much is covered ([covered], --native-top-bar-height). A chat
 *   always does; any other page where the server says its stylesheet does
 *   ([PathConfigurationProperties.pageUnderTopBar]).
 * - Otherwise (an older server) the page starts below the bar, and the
 *   fade's last stretch, past the bar's edge, comes in as the page scrolls
 *   ([follow]), so a page at its top shows its first line whole.
 */
class TopBar(root: View, val under: Boolean) {
    private val bar: View = root.findViewById(R.id.app_bar)
    private val fade: View = root.findViewById(R.id.top_fade)
    private val spinners =
        listOf(HotwireR.id.hotwire_webView_container, HotwireR.id.hotwire_error_refresh)
            .mapNotNull { root.findViewById<SwipeRefreshLayout>(it) }

    init {
        fade.background = fade(root.context)
        if (!under) {
            root.findViewById<View>(R.id.page_area).updateLayoutParams<RelativeLayout.LayoutParams> {
                addRule(RelativeLayout.BELOW, R.id.app_bar)
            }
            fade.alpha = 0f
        }
    }

    /** How much of the page the bar covers, status bar included: 0 where the page starts below it. */
    val covered: Int
        get() = if (under) bar.height else 0

    /**
     * Calls [onCoveredChanged] whenever the bar's height changes (the status
     * bar's inset arriving, a rotation), with the pull to refresh's spinners,
     * the page's and an error's, already moved below it.
     */
    fun onCoveredChanged(onCoveredChanged: () -> Unit) {
        bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            if (under) {
                spinners.forEach { spinner ->
                    val circle = spinner.progressCircleDiameter
                    spinner.setProgressViewOffset(false, covered - circle, covered + circle / 2)
                }
            }
            onCoveredChanged()
        }
    }

    /**
     * Follows [webView]'s scrolling, which the screens share: a page below the
     * bar brings the fade's overhang in over its first [overhang] pixels of
     * scroll. A page under the bar needs nothing, and drops the last screen's.
     */
    fun follow(webView: WebView) {
        if (under) {
            webView.setOnScrollChangeListener(null)
            return
        }
        val overhang = -bar.resources.getDimension(R.dimen.top_fade_overhang)
        val show = { y: Int -> (y / overhang).coerceIn(0f, 1f) }
        fade.alpha = show(webView.scrollY)
        // The web view outlives this screen, which it mustn't hold on to.
        val weakFade = WeakReference(fade)
        webView.setOnScrollChangeListener { _, _, y, _, _ -> weakFade.get()?.alpha = show(y) }
    }

    private companion object {
        val STOPS = floatArrayOf(0f, 0.68f, 0.76f, 0.84f, 0.92f, 1f)
        val ALPHAS = listOf(255, 255, 224, 140, 46, 0)

        /** The page's canvas down past the title, then an ease rather than a ramp, whose ends would show as lines. */
        fun fade(context: Context): Drawable {
            val canvas = ContextCompat.getColor(context, R.color.lw_canvas)
            val colors = ALPHAS.map { ColorUtils.setAlphaComponent(canvas, it) }.toIntArray()
            return PaintDrawable().apply {
                // Without a shape it ignores the shader and fills in black.
                shape = RectShape()
                shaderFactory =
                    object : ShapeDrawable.ShaderFactory() {
                        override fun resize(width: Int, height: Int): Shader =
                            LinearGradient(0f, 0f, 0f, height.toFloat(), colors, STOPS, Shader.TileMode.CLAMP)
                    }
            }
        }
    }
}

/**
 * The page keeps clear of the top bar itself (--native-top-bar-height), so it
 * runs up under it. The server sets it in its path configuration once its
 * stylesheet does; a chat runs under the bar whatever the configuration says.
 */
val HashMap<String, Any>.pageUnderTopBar: Boolean
    get() = get(PAGE_UNDER_TOP_BAR) as? Boolean ?: false

const val PAGE_UNDER_TOP_BAR = "page_under_top_bar"
