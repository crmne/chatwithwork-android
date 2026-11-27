package com.chatwithwork.app.fragments

import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PageScriptsTest {
    @Test
    fun `gives the page the bar's height in its own pixels`() {
        assertThat(PageScripts.topBarHeightScript(280, 1080))
            .isEqualTo(
                "document.documentElement.style" +
                    ".setProperty('--native-top-bar-height', (280 * window.innerWidth / 1080) + 'px');" +
                    "delete document.documentElement.dataset.nativeTopBar"
            )
    }

    @Test
    fun `marks a page the bar floats over, which draws what's behind it`() {
        assertThat(PageScripts.topBarHeightScript(280, 1080, floats = true))
            .endsWith(";document.documentElement.dataset.nativeTopBar = 'floating'")
    }

    // A screen coming back after Android ended the app tells the page before its web view is laid out: the page ran
    // under the bar, never told how much of it the bar covers.
    @Test
    fun `tells a view not laid out yet once it is, the newest height only`() {
        val view = View(RuntimeEnvironment.getApplication())
        val told = mutableListOf<String>()

        PageScripts.whenLaidOut(view) { _, width -> told += "120 at $width" }
        PageScripts.whenLaidOut(view) { _, width -> told += "280 at $width" }
        assertThat(told).isEmpty()

        view.layout(0, 0, 1080, 2000)
        assertThat(told).containsExactly("280 at 1080")
    }

    @Test
    fun `tells a laid out view at once`() {
        val view = View(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 1080, 2000)
        val told = mutableListOf<Int>()

        PageScripts.whenLaidOut(view) { _, width -> told += width }
        assertThat(told).containsExactly(1080)
    }
}
