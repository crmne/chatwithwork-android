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

    init {
        drawer.addDrawerListener(this)
    }

    /** Runs [action] now if the drawer is shut, else once it has slid shut and been drawn so. */
    fun run(action: () -> Unit) {
        if (!drawer.isDrawerVisible(GravityCompat.START)) {
            pending = null
            action()
            return
        }
        pending = action
        drawer.closeDrawer(GravityCompat.START)
    }

    override fun onDrawerClosed(drawerView: View) {
        val action = pending ?: return
        pending = null
        afterNextFrame(drawer) {
            if (drawer.isAttachedToWindow && !drawer.isDrawerVisible(GravityCompat.START)) action()
        }
    }

    // Opened again before it shut: the person changed their mind.
    override fun onDrawerOpened(drawerView: View) {
        pending = null
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
