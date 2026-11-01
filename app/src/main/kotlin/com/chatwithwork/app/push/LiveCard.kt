package com.chatwithwork.app.push

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R
import kotlin.math.roundToInt

/**
 * A Live Update's views: the iOS app's Lock Screen card (`ChatLiveActivity.swift`)
 * in RemoteViews, dark in both themes on purpose, with the road as a picture
 * ([LiveArtwork]) and everything else real, so the clock runs on its own and
 * TalkBack reads the words. Every view's state is set on every update, since
 * the system may reapply these onto the views the last update left behind.
 */
class LiveCard(
    private val context: Context,
    private val shown: LivePresentation,
    private val title: String,
    private val organization: String,
    private val steps: String?,
    private val clock: Clock,
    private val open: PendingIntent
) {
    sealed interface Clock {
        /** Counting up from a moment on the wall clock. */
        data class Running(val since: Long) : Clock

        /** How long it took, once it's over. */
        data class Fixed(val millis: Long) : Clock

        data object Hidden : Clock
    }

    fun collapsed() = RemoteViews(context.packageName, R.layout.notification_live_collapsed).apply {
        setTextViewText(R.id.live_title, title)
        setTime(this, R.id.live_clock, R.id.live_time)
    }

    fun expanded() = RemoteViews(context.packageName, R.layout.notification_live_card).apply {
        words(R.id.live_organization, organization, ORGANIZATION, R.color.road_ink_muted)
        words(R.id.live_steps, steps.orEmpty(), STEPS, R.color.road_ink_muted)
        setViewVisibility(R.id.live_steps, visible(steps != null))
        setViewVisibility(R.id.live_headline, visible(!shown.hasBand))
        setViewVisibility(R.id.live_band, visible(shown.hasBand))
        headline()
        road()
        if (shown.hasBand) band()
        setContentDescription(R.id.live_card, listOfNotNull(organization, title, steps).joinToString(". "))
        setOnClickPendingIntent(R.id.live_action, open)
    }

    /** Words as a picture, which TalkBack reads as words. It shrinks to fit rather than cut them off. */
    private fun RemoteViews.words(
        @IdRes view: Int,
        text: String,
        type: LiveArtwork.Type,
        @ColorRes color: Int,
        logo: String? = null,
        after: String? = null,
        logoSizeSp: Float = 0f
    ) {
        val picture = LiveArtwork.words(context, text, type, color(color), logo, after, logoSizeSp)
        setImageViewIcon(view, picture.icon)
        setInt(view, "setMaxHeight", picture.height)
        setContentDescription(view, listOfNotNull(text, after).joinToString(" "))
    }

    private fun RemoteViews.headline() {
        val working = shown.state == LivePresentation.State.WORKING
        val split = shown.split(title).takeIf { working }
        val color = when (shown.state) {
            LivePresentation.State.FINISHED -> R.color.road_positive
            LivePresentation.State.QUIET -> R.color.road_ink_muted
            else -> R.color.road_ink
        }
        val logo = shown.service.takeIf { working && LiveServices.of(it)?.logo != null }
        words(R.id.live_title, split?.first ?: title, HEADLINE, color, logo, split?.second, HEADLINE_LOGO)
        setTextColor(R.id.live_clock, color(color))
        setTextColor(R.id.live_time, color(color))
        setTime(this, R.id.live_clock, R.id.live_time, !shown.hasBand)
    }

    private fun RemoteViews.road() {
        val height = if (shown.hasBand) BAND_ROAD_HEIGHT else ROAD_HEIGHT
        setImageViewIcon(R.id.live_road, LiveArtwork.road(context, shown, roadWidth(context), height))
        setInt(R.id.live_road, "setMaxHeight", (height * context.resources.displayMetrics.density).roundToInt())
        setContentDescription(R.id.live_road, title)
    }

    private fun RemoteViews.band() {
        val decision = shown.state == LivePresentation.State.DECISION
        val input = shown.state == LivePresentation.State.INPUT
        val ink = when {
            decision -> R.color.road_canvas
            input -> R.color.road_attention
            else -> R.color.road_negative
        }
        val actionInk = when {
            decision -> R.color.road_attention
            input -> R.color.road_canvas
            else -> R.color.road_negative
        }
        setInt(
            R.id.live_band,
            "setBackgroundResource",
            when {
                decision -> R.drawable.live_band_decision
                input -> R.drawable.live_band_input
                else -> R.drawable.live_band_failed
            }
        )
        words(R.id.live_band_title, context.getString(shown.headline), BAND_TITLE, ink)

        val service = LiveServices.of(shown.service)?.takeIf { decision && it.name.isNotEmpty() }
        setViewVisibility(R.id.live_band_where, visible(service != null))
        if (service != null) {
            val where = context.getString(R.string.live_update_in)
            words(R.id.live_band_where, where, BAND_WHERE, ink, shown.service, service.name, BAND_LOGO)
        }

        setViewVisibility(R.id.live_band_clocks, visible(shown.isWaiting))
        words(R.id.live_band_waiting, context.getString(R.string.live_update_waiting), BAND_WAITING, ink)
        setTextColor(R.id.live_band_clock, color(ink))
        setTextColor(R.id.live_band_time, color(ink))
        setTime(this, R.id.live_band_clock, R.id.live_band_time, shown.isWaiting)

        setInt(
            R.id.live_action,
            "setBackgroundResource",
            when {
                decision -> R.drawable.live_pill_canvas
                input -> R.drawable.live_pill_attention
                else -> 0
            }
        )
        words(R.id.live_action_label, shown.action?.let(context::getString).orEmpty(), BAND_ACTION, actionInk)
        setInt(R.id.live_action_chevron, "setColorFilter", color(actionInk))
    }

    /** A running clock is the system's own, so it counts between pushes. */
    private fun setTime(views: RemoteViews, @IdRes running: Int, @IdRes fixed: Int, shows: Boolean = true) {
        val since = (clock as? Clock.Running)?.since
        val total = (clock as? Clock.Fixed)?.millis
        views.setViewVisibility(running, visible(shows && since != null))
        views.setViewVisibility(fixed, visible(shows && total != null))
        val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - (since ?: 0L))
        views.setChronometer(running, base, null, shows && since != null)
        views.setTextViewText(fixed, total?.let { DateUtils.formatElapsedTime(it / MILLIS) }.orEmpty())
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(context, id)

    private fun visible(shows: Boolean) = if (shows) View.VISIBLE else View.GONE

    companion object {
        private const val ROAD_HEIGHT = 52f

        /** 48 of road, under the 6 of air the band's card has above it. */
        private const val BAND_ROAD_HEIGHT = 54f
        private const val MILLIS = 1000
        private const val MAX_WIDTH = 480f
        private const val MIN_WIDTH = 240f

        private val ORGANIZATION = LiveArtwork.Type(R.font.geist_medium, 12.5f)
        private val STEPS = LiveArtwork.Type(R.font.geist_mono_medium, 11.5f)
        private val HEADLINE = LiveArtwork.Type(R.font.geist_semibold, 21f, -0.02f)
        private val BAND_TITLE = LiveArtwork.Type(R.font.geist_semibold, 17f, -0.015f)
        private val BAND_WHERE = LiveArtwork.Type(R.font.geist_medium, 12.5f)
        private val BAND_WAITING = LiveArtwork.Type(R.font.geist_mono_medium, 10.5f)
        private val BAND_ACTION = LiveArtwork.Type(R.font.geist_semibold, 13.5f)
        private const val HEADLINE_LOGO = 20f
        private const val BAND_LOGO = 14f

        /** What the shade's sides and the card's own padding leave of the screen: Android 16's indent is wider. */
        private const val INSETS = 100 + 28
        private const val INSETS_BAKLAVA = 120 + 28

        /** The road's width in dp. It's drawn to fit and scales down if a shade is narrower than this phone's. */
        fun roadWidth(context: Context): Float {
            val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) INSETS_BAKLAVA else INSETS
            return (context.resources.configuration.screenWidthDp - insets).toFloat().coerceIn(MIN_WIDTH, MAX_WIDTH)
        }
    }
}
