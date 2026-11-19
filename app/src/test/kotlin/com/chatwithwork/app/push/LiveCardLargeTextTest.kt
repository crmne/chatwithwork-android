package com.chatwithwork.app.push

import android.app.PendingIntent
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawn Live Update below Android 16 at the system's largest text: the
 * chat's title stays in both sizes of the card, cut short at its end at worst,
 * and leaves the steps count its room.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h693dp-xxhdpi")
class LiveCardLargeTextTest {
    private val title = "Acme renewal and the Q3 pricing review with legal"

    private fun card(shown: LivePresentation, steps: String? = "12 steps"): LiveCard {
        RuntimeEnvironment.setFontScale(2f)
        val context = RuntimeEnvironment.getApplication()
        val open = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        return LiveCard(
            context,
            shown,
            shown.title("Searching Todoist and Google Drive for the pricing sheet") { "Working" },
            shown.chatTitle ?: "Northwind Studio",
            steps,
            LiveCard.Clock.Running(System.currentTimeMillis()),
            open
        )
    }

    private fun apply(views: android.widget.RemoteViews): View {
        val context = RuntimeEnvironment.getApplication()
        return views.apply(context, FrameLayout(context))
    }

    @Test
    fun `collapsed, at 200 percent, the line starts with the chat's title and TalkBack hears it`() {
        val shown = LivePresentation("answering", "search", steps = 12, chatTitle = title)
        val view = apply(card(shown).collapsed())
        assertThat(view.findViewById<TextView>(R.id.live_title).text.toString()).startsWith(title)
        assertThat(view.findViewById<View>(R.id.live_collapsed).contentDescription.toString()).startsWith(title)
    }

    @Test
    fun `expanded, at 200 percent, the title is cut at its end before it pushes the steps count out`() {
        listOf("answering", "approval_waiting", "input_requested", "answered", "failed", "stopped").forEach { phase ->
            val shown = LivePresentation(phase, "search", steps = 12, trail = "ssssssssssss", chatTitle = title)
            val view = apply(card(shown).expanded())
            val density = view.resources.displayMetrics.density
            val heading = view.findViewById<ImageView>(R.id.live_organization).drawable.intrinsicWidth / density
            val steps = view.findViewById<ImageView>(R.id.live_steps).drawable.intrinsicWidth / density
            // The mark and its gap (22dp), the heading, the steps' gap (8dp) and the steps fit the card's width.
            assertThat(22 + heading + 8 + steps).isAtMost(LiveCard.contentWidth(view.context) + 1)
            assertThat(heading).isGreaterThan(40f)
            assertThat(view.findViewById<View>(R.id.live_card).contentDescription.toString()).startsWith(title)
        }
    }

    @Test
    fun `at 200 percent the step's words beside the clock are cut short at their end, not shrunk`() {
        val shown =
            LivePresentation("answering", "search", steps = 3, trail = "sss", service = "todoist", chatTitle = title)
        val view = apply(card(shown).expanded())
        val density = view.resources.displayMetrics.density
        val headline = view.findViewById<ImageView>(R.id.live_title).drawable.intrinsicWidth / density
        val clock = android.text.TextPaint().apply {
            // As the clock's TextView sizes its 20sp, with Android 14's nonlinear font scaling.
            textSize = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                20f,
                view.resources.displayMetrics
            )
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }.measureText("00:00") / density
        // Drawn at its full size, the picture and the clock share the card's width, so nothing scales it down.
        assertThat(headline + 12 + clock).isAtMost(LiveCard.contentWidth(view.context) + 1)
    }
}
