package com.chatwithwork.app.composer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The edge and its glow drawn to pixels, state by state: under any point of
 * the edge the glow is that point's colour, at rest, while it flows, and
 * amber, as the web's composer-flow moves both layers as one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 35], qualifiers = "w411dp-h891dp-mdpi")
class ComposerGlowTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        .apply { setTheme(R.style.Theme_ChatWithWork) }

    private val box = Rect(MARGIN, MARGIN, MARGIN + WIDTH, MARGIN + HEIGHT)

    /** The glow and the box, as ComposerView.dispatchDraw draws them, on the canvas colour. */
    private fun draw(look: ComposerLook, flow: Float = 0f, glowOpacity: Float = look.glowOpacity): Bitmap {
        val background = ComposerBackground(activity).apply {
            setBounds(0, 0, WIDTH, HEIGHT)
            this.look = look
            edgeOpacity = 1f
            this.flow = flow
        }
        val bitmap = Bitmap.createBitmap(WIDTH + MARGIN * 2, HEIGHT + MARGIN * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        ComposerGlow(activity).draw(canvas, box, background.edgeShader, background.attentionColor, glowOpacity * 4)
        canvas.save()
        canvas.translate(box.left.toFloat(), box.top.toFloat())
        background.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun hue(color: Int): Float = FloatArray(3).also { Color.colorToHSV(color, it) }[0]

    private fun hueApart(a: Int, b: Int): Float = abs(hue(a) - hue(b)).let { minOf(it, 360 - it) }

    /** The edge's colour at [x] across the box, and the glow's just above it, clear of the shadow below. */
    private fun edgeAndGlow(bitmap: Bitmap, x: Float): Pair<Int, Int> {
        val column = box.left + (WIDTH * x).toInt()
        return bitmap.getPixel(column, box.top + 1) to bitmap.getPixel(column, box.top - 8)
    }

    private fun assertGlowFollowsEdge(bitmap: Bitmap) {
        listOf(0.15f, 0.3f, 0.5f, 0.7f, 0.85f).forEach { x ->
            val (edge, glow) = edgeAndGlow(bitmap, x)
            assertThat(Color.alpha(glow)).isEqualTo(255)
            assertThat(hueApart(edge, glow)).isLessThan(12f)
        }
    }

    @Test
    fun `at rest the glow under each point of the edge is its colour`() {
        assertGlowFollowsEdge(draw(ComposerLook()))
    }

    @Test
    fun `while an answer runs the glow flows with the edge, in step`() {
        val start = draw(ComposerLook(working = true), flow = 0f)
        val later = draw(ComposerLook(working = true), flow = 0.3f)
        assertGlowFollowsEdge(start)
        assertGlowFollowsEdge(later)
        // It moved: the same place shows another colour, in the edge and the glow alike.
        val (edgeBefore, glowBefore) = edgeAndGlow(start, 0.5f)
        val (edgeAfter, glowAfter) = edgeAndGlow(later, 0.5f)
        assertThat(hueApart(edgeBefore, edgeAfter)).isGreaterThan(40f)
        assertThat(hueApart(glowBefore, glowAfter)).isGreaterThan(40f)
    }

    @Test
    fun `a change waiting turns the edge and the glow amber`() {
        val bitmap = draw(ComposerLook(waiting = true, working = true))
        val amber = activity.getColor(R.color.lw_attention)
        listOf(0.15f, 0.5f, 0.85f).forEach { x ->
            val (edge, glow) = edgeAndGlow(bitmap, x)
            assertThat(hueApart(edge, amber)).isLessThan(4f)
            assertThat(hueApart(glow, amber)).isLessThan(6f)
        }
    }

    @Test
    fun `focus brightens the glow`() {
        val rest = draw(ComposerLook())
        val focused = draw(ComposerLook(focused = true))
        val (_, restGlow) = edgeAndGlow(rest, 0.5f)
        val (_, focusedGlow) = edgeAndGlow(focused, 0.5f)
        val brightness = { c: Int -> Color.red(c) + Color.green(c) + Color.blue(c) }
        assertThat(brightness(focusedGlow)).isGreaterThan(brightness(restGlow) * 3 / 2)
    }

    @Test
    fun `the composer flows from Send until its answer runs, and stops if none does`() {
        val composer = composer()
        val field = composer.findViewById<EditText>(R.id.composer_field)
        val send = composer.findViewById<View>(R.id.composer_send)

        field.setText("What changed in the Q3 plan?")
        send.performClick()
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)

        // The field clearing sends a state still idle: it keeps flowing.
        composer.show(ComposerState())
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)

        // The server's answer runs, then ends: at rest again.
        composer.show(ComposerState(working = true))
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)
        composer.show(ComposerState())
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.RAINBOW)

        // A send nothing answers stops flowing once the web's request would have.
        field.setText("And the Q4 one?")
        send.performClick()
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.RAINBOW)

        // A dialog taking the screen (model consent) ends it at once.
        field.setText("Once more")
        send.performClick()
        composer.show(ComposerState(visible = false))
        composer.show(ComposerState())
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.RAINBOW)
    }

    @Test
    fun `the composer turns amber while a change waits, and back`() {
        val composer = composer()
        composer.show(ComposerState(working = true))
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)
        composer.show(ComposerState(waiting = true))
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.ATTENTION)
        composer.show(ComposerState(working = true))
        assertThat(composer.shownLook.edge).isEqualTo(ComposerLook.Edge.FLOWING)
    }

    @Test
    fun `focusing the field brightens the composer`() {
        val composer = composer()
        assertThat(composer.shownLook.focused).isFalse()
        composer.findViewById<EditText>(R.id.composer_field).requestFocus()
        assertThat(composer.shownLook.focused).isTrue()
        assertThat(composer.shownLook.edgeOpacity).isEqualTo(1f)
    }

    private fun composer(): ComposerView {
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val composer = LayoutInflater.from(activity).inflate(R.layout.view_composer, root, false) as ComposerView
        root.addView(composer)
        composer.show(ComposerState())
        shadowOf(Looper.getMainLooper()).idle()
        return composer
    }

    companion object {
        private const val WIDTH = 360
        private const val HEIGHT = 120
        private const val MARGIN = 80
    }
}
