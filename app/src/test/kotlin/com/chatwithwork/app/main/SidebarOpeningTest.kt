package com.chatwithwork.app.main

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SidebarOpeningTest {
    private val projects = "https://chatwithwork.com/482139075/projects"

    @Test
    fun `the screen the sidebar opened takes it`() {
        val opening = SidebarOpening()
        opening.opening(projects)

        assertThat(opening.take(projects)).isTrue()
    }

    @Test
    fun `the same page opened again by a link does not`() {
        val opening = SidebarOpening()
        opening.opening(projects)
        opening.take(projects)

        assertThat(opening.take(projects)).isFalse()
    }

    @Test
    fun `another screen ends it`() {
        val opening = SidebarOpening()
        opening.opening(projects)

        assertThat(opening.take("https://chatwithwork.com/482139075/bots")).isFalse()
        assertThat(opening.take(projects)).isFalse()
    }

    @Test
    fun `nothing is open until the sidebar opens it`() {
        assertThat(SidebarOpening().take(projects)).isFalse()
    }
}
