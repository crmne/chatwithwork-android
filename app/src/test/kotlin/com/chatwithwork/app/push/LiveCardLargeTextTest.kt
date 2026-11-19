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

    private fun card(
        shown: LivePresentation,
        steps: String? = "12 steps",
        since: Long = System.currentTimeMillis()
    ): LiveCard {
        RuntimeEnvironment.setFontScale(2f)
        val context = RuntimeEnvironment.getApplication()
        val open = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        return LiveCard(
            context,
            shown,
            shown.title("Searching Todoist and Google Drive for the pricing sheet") { "Working" },
            shown.chatTitle ?: "Northwind Studio",
            steps,
            LiveCard.Clock.Running(since),
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
    fun `expanded, at 200 percent, the title is cut at its end, never shrunk or pushed down to a few letters`() {
        listOf("answering", "approval_waiting", "input_requested", "answered", "failed", "stopped").forEach { phase ->
            val shown = LivePresentation(phase, "search", steps = 12, trail = "ssssssssssss", chatTitle = title)
            val view = apply(card(shown).expanded())
            val density = view.resources.displayMetrics.density
            val heading = view.findViewById<ImageView>(R.id.live_organization).drawable.intrinsicWidth / density
            val stepsView = view.findViewById<ImageView>(R.id.live_steps)
            // The steps count keeps its place beside the title unless it would leave the title a few letters.
            val steps = if (stepsView.visibility ==
                View.VISIBLE
            ) {
                8 + stepsView.drawable.intrinsicWidth / density
            } else {
                0f
            }
            // The mark and its gap (22dp), the heading, and the steps with their gap fit the card's width.
            assertThat(22 + heading + steps).isAtMost(LiveCard.contentWidth(view.context) + 1)
            // More than a few letters of the title: half the card's width at least.
            assertThat(heading).isAtLeast(LiveCard.contentWidth(view.context) / 2)
            assertThat(view.findViewById<View>(R.id.live_card).contentDescription.toString()).startsWith(title)
        }
    }

    @Test
    fun `a card that could pass an hour before it times out keeps room for the clock's hours`() {
        val shown = LivePresentation("answering", "search", steps = 3, trail = "sss", chatTitle = title)
        // 40 minutes in: the next update may never come, and the clock reads 1:00:00 before the card goes at 70.
        val view = apply(card(shown, since = System.currentTimeMillis() - 40 * 60 * 1000L).expanded())
        val density = view.resources.displayMetrics.density
        val headline = view.findViewById<ImageView>(R.id.live_title).drawable.intrinsicWidth / density
        val room = LiveCard.contentWidth(view.context) - 12 - clockWidth(view, "0:00:00")
        assertThat(headline * LiveCard.HEADLINE_LEAST).isAtMost(room + 1)
    }

    @Test
    fun `25 minutes on, with no update, the clock passes an hour and the step line keeps 60 percent`() {
        val shown = LivePresentation("answering", "search", steps = 3, trail = "sss", chatTitle = title)
        val view = apply(card(shown, since = System.currentTimeMillis() - 40 * 60 * 1000L).expanded())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(25))
        val clock = view.findViewById<android.widget.Chronometer>(R.id.live_clock)
        // As the running clock reads by itself, the card never reposted.
        clock.base = clock.base
        assertThat(clock.text.toString()).matches("1:0\\d:\\d\\d")
        val density = view.resources.displayMetrics.density
        val row = view.findViewById<View>(R.id.live_headline)
        val width = (LiveCard.contentWidth(view.context) * density).toInt()
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        val headline = view.findViewById<ImageView>(R.id.live_title)
        // The step's words get at least 60% of their drawn width beside the clock's hours.
        assertThat(headline.width.toFloat()).isAtLeast(headline.drawable.intrinsicWidth * LiveCard.HEADLINE_LEAST - 2)
    }

    private fun clockWidth(view: View, sample: String): Float = android.text.TextPaint().apply {
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            20f,
            view.resources.displayMetrics
        )
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }.measureText(sample) / view.resources.displayMetrics.density

    @Test
    fun `before the first step, with no count, the title has the whole row`() {
        val shown = LivePresentation("answering", "answer", chatTitle = title)
        val view = apply(card(shown, steps = null).expanded())
        val density = view.resources.displayMetrics.density
        val heading = view.findViewById<ImageView>(R.id.live_organization).drawable.intrinsicWidth / density
        assertThat(view.findViewById<View>(R.id.live_steps).visibility).isEqualTo(View.GONE)
        // Beyond what the 88dp kept for a count would have left it.
        assertThat(heading).isGreaterThan(LiveCard.contentWidth(view.context) - 22 - 88)
        assertThat(22 + heading).isAtMost(LiveCard.contentWidth(view.context) + 1)
    }

    @Test
    fun `at 200 percent the step's words beside the clock shrink only so far, then are cut short at their end`() {
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
        // The picture shrinks to share the card's width with the clock, but never below 60% of its size: what's past
        // that is cut short, so the words stay large enough to read.
        val room = LiveCard.contentWidth(view.context) - 12 - clock
        assertThat(headline * LiveCard.HEADLINE_LEAST).isAtMost(room + 1)
    }
}
