package com.chatwithwork.app.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The system back, as Google's navigation principles have it for a drawer's top-level destinations. */
class GoingBackTest {
    private val home = "https://chatwithwork.com/482139075/decisions"

    @Test
    fun `at a place other than the home, back goes home`() {
        assertThat(GoingBack.step(sidebarOpen = false, depth = 1, atHome = false, home = home))
            .isEqualTo(GoingBack.Step.GoHome(home))
    }

    @Test
    fun `at the home, back leaves the app`() {
        assertThat(GoingBack.step(sidebarOpen = false, depth = 1, atHome = true, home = home))
            .isEqualTo(GoingBack.Step.Leave)
    }

    @Test
    fun `with no home known, back leaves the app`() {
        assertThat(GoingBack.step(sidebarOpen = false, depth = 1, atHome = false, home = null))
            .isEqualTo(GoingBack.Step.Leave)
    }

    @Test
    fun `in a drill-down or a modal, back pops one step`() {
        assertThat(GoingBack.step(sidebarOpen = false, depth = 2, atHome = false, home = home))
            .isEqualTo(GoingBack.Step.Pop)
        assertThat(GoingBack.step(sidebarOpen = false, depth = 3, atHome = true, home = home))
            .isEqualTo(GoingBack.Step.Pop)
    }

    @Test
    fun `with the sidebar open, back closes it first`() {
        assertThat(GoingBack.step(sidebarOpen = true, depth = 1, atHome = false, home = home))
            .isEqualTo(GoingBack.Step.CloseSidebar)
        assertThat(GoingBack.step(sidebarOpen = true, depth = 2, atHome = true, home = home))
            .isEqualTo(GoingBack.Step.CloseSidebar)
    }
}
