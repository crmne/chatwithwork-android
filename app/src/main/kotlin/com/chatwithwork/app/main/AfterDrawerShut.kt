package com.chatwithwork.app.main

import android.os.Build
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

/**
 * Opens what the sidebar asks for over the page (New chat, New bot, Rename,
 * Move to project: modals) only once the drawer has slid shut and the screen
 * without it has been drawn.
 *
 * Opening a modal takes two pictures of the screen as it's shown: the frame's
 * backdrop (ModalBackdrop) and Hotwire Native's own, which the screen under
 * the modal shows while the shared web view is away, and again after the
 * modal closes until the page has drawn. Taken while the drawer was still on
 * screen, both had the drawer in them: it vanished, came back behind the
 * rising card, and came back again on every close. So the drawer closes
 * first, with its own slide, and the modal rises over the page alone.
 */
class AfterDrawerShut(
    private val drawer: DrawerLayout,
    private val afterNextFrame: (View, () -> Unit) -> Unit = Companion::afterNextFrame
) : DrawerLayout.SimpleDrawerListener() {
    private var pending: (() -> Unit)? = null

    /** Counts the requests: one waiting on its frame opens only if nothing newer was asked since. */
    private var generation = 0

    init {
        drawer.addDrawerListener(this)
    }

    /** Runs [action] now if the drawer is shut, else once it has slid shut and been drawn so. */
    fun run(action: () -> Unit) {
        cancel()
        if (!drawer.isDrawerVisible(GravityCompat.START)) {
            action()
            return
        }
        pending = action
        drawer.closeDrawer(GravityCompat.START)
    }

    /** How far out the drawer is (1 open, 0 shut), followed always, to tell it turning back open from sliding shut. */
    private var lastOffset = 0f

    /** Something newer was chosen (a place, signing out): what waits for the drawer never opens. */
    fun cancel() {
        pending = null
        generation++
    }

    override fun onDrawerClosed(drawerView: View) {
        lastOffset = 0f
        val action = pending ?: return
        pending = null
        val asked = generation
        afterNextFrame(drawer) {
            val current = asked == generation && drawer.isAttachedToWindow
            if (current && !drawer.isDrawerVisible(GravityCompat.START)) action()
        }
    }

    // Turned back open, even partly, before it shut: the person changed their mind.
    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        if (pending != null && slideOffset > lastOffset) cancel()
        lastOffset = slideOffset
    }

    override fun onDrawerOpened(drawerView: View) {
        lastOffset = 1f
        cancel()
    }

    companion object {
        /** Runs [action] once the next frame of [view]'s window, drawn as it is now, has been handed to the screen. */
        fun afterNextFrame(view: View, action: () -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.viewTreeObserver.registerFrameCommitCallback { view.post(action) }
                view.invalidate()
            } else {
                view.postOnAnimation { view.post(action) }
            }
        }
    }
}
