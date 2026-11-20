package com.chatwithwork.app.main

import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the sidebar opens over the page waits for the drawer to slide shut and
 * the screen without it to be drawn, so the pictures a modal takes as it opens
 * never have the drawer in them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AfterDrawerShutTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    private val panel = View(activity)
    private val drawer =
        DrawerLayout(activity).apply {
            addView(FrameLayout(activity))
            addView(panel, DrawerLayout.LayoutParams(300, DrawerLayout.LayoutParams.MATCH_PARENT, Gravity.START))
            activity.setContentView(this)
        }

    /** Frames drawn so far: each waiting action runs as its frame is. */
    private val frames = mutableListOf<() -> Unit>()
    private val afterDrawerShut = AfterDrawerShut(drawer) { _, action -> frames += action }

    private val opened = mutableListOf<String>()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun opensAtOnceWhenTheDrawerIsShut() {
        afterDrawerShut.run { opened += "new chat" }

        assertThat(opened).containsExactly("new chat")
    }

    @Test
    fun waitsForTheDrawerToShutAndBeDrawn() {
        drawer.openDrawer(Gravity.START, false)
        idle()

        afterDrawerShut.run { opened += "new bot" }
        assertThat(opened).isEmpty()

        drawer.closeDrawer(Gravity.START, false)
        idle()
        assertThat(opened).isEmpty()

        frames.forEach { it() }
        assertThat(opened).containsExactly("new bot")
    }

    @Test
    fun forgetsItWhenTheDrawerOpensAgainFirst() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }

        afterDrawerShut.onDrawerOpened(panel)
        drawer.closeDrawer(Gravity.START, false)
        idle()
        frames.forEach { it() }

        assertThat(opened).isEmpty()
    }

    @Test
    fun onlyTheLastAskOpens() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }
        afterDrawerShut.run { opened += "new chat" }

        drawer.closeDrawer(Gravity.START, false)
        idle()
        frames.forEach { it() }

        assertThat(opened).containsExactly("new chat")
    }

    @Test
    fun aPlaceChosenMeanwhileWins() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }

        afterDrawerShut.cancel()
        drawer.closeDrawer(Gravity.START, false)
        idle()
        frames.forEach { it() }

        assertThat(opened).isEmpty()
    }

    @Test
    fun aPlaceChosenWhileItWaitsOnItsFrameWins() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }
        drawer.closeDrawer(Gravity.START, false)
        idle()

        afterDrawerShut.cancel()
        frames.forEach { it() }

        assertThat(opened).isEmpty()
    }

    @Test
    fun turningTheDrawerBackOpenPartlyForgetsIt() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }

        afterDrawerShut.onDrawerSlide(panel, 0.6f)
        afterDrawerShut.onDrawerSlide(panel, 0.7f)
        drawer.closeDrawer(Gravity.START, false)
        idle()
        frames.forEach { it() }

        assertThat(opened).isEmpty()
    }

    @Test
    fun aNewerAskStillNoticesTheDrawerTurningBack() {
        drawer.openDrawer(Gravity.START, false)
        idle()
        afterDrawerShut.run { opened += "new bot" }
        afterDrawerShut.onDrawerSlide(panel, 0.6f)

        afterDrawerShut.run { opened += "new chat" }
        afterDrawerShut.onDrawerSlide(panel, 0.7f)
        drawer.closeDrawer(Gravity.START, false)
        idle()
        frames.forEach { it() }

        assertThat(opened).isEmpty()
    }
}
