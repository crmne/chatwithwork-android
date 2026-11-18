package com.chatwithwork.app.sidebar

import com.chatwithwork.app.sidebar.SidebarPageState.Change
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which load of the sidebar page shows (docs/server-contract.md, "The sidebar page", "When it can't load"). */
class SidebarPageStateTest {
    private val page = "/1000001/sidebar"
    private val other = "/2000002/sidebar"

    private fun drawn(path: String = page) = SidebarPageState().apply {
        show(path)
        ready(document, path)
    }

    @Test
    fun `loads a page once, and shows it when it says it drew`() {
        val state = SidebarPageState()
        assertThat(state.show(page)).isEqualTo(Change.LOAD)
        assertThat(state.show(page)).isEqualTo(Change.NONE)
        assertThat(state.showing).isFalse()
        assertThat(state.ready(state.document, page)).isTrue()
        assertThat(state.showing).isTrue()
    }

    @Test
    fun `a reload that fails goes back to the native sidebar and tries again as the drawer opens`() {
        val state = drawn()
        assertThat(state.failed(500)).isFalse()
        assertThat(state.showing).isFalse()
        assertThat(state.retry()).isTrue()
        assertThat(state.retry()).isFalse()
        assertThat(state.ready(state.document, page)).isTrue()
        assertThat(state.showing).isTrue()
    }

    @Test
    fun `the offline copy drawing after a failed load shows`() {
        val state = drawn()
        state.failed(null)
        assertThat(state.ready(state.document, page)).isTrue()
        assertThat(state.showing).isTrue()
        assertThat(state.failed).isFalse()
    }

    @Test
    fun `a page that answers 404 isn't asked for again, another one is`() {
        val state = drawn()
        assertThat(state.failed(404)).isTrue()
        assertThat(state.show(null)).isEqualTo(Change.CLEAR)
        assertThat(state.show(page)).isEqualTo(Change.NONE)
        assertThat(state.path).isNull()
        assertThat(state.show(other)).isEqualTo(Change.LOAD)
        // Signing in again asks afresh.
        state.forget()
        assertThat(state.show(page)).isEqualTo(Change.LOAD)
    }

    @Test
    fun `a failure that isn't 404 stays retryable by the next connect`() {
        val state = SidebarPageState()
        state.show(page)
        assertThat(state.failed(503)).isFalse()
        assertThat(state.show(page)).isEqualTo(Change.LOAD)
    }

    @Test
    fun `ready from a document the app replaced or dropped is refused`() {
        val state = drawn()
        val before = state.document
        state.show(other)
        // The old page's ready, arriving late, while the web view still shows it.
        assertThat(state.ready(before, page)).isFalse()
        assertThat(state.ready(state.document, page)).isFalse()
        assertThat(state.showing).isFalse()
        assertThat(state.ready(state.document, other)).isTrue()

        val shown = state.document
        assertThat(state.forget()).isEqualTo(Change.CLEAR)
        assertThat(state.ready(shown, other)).isFalse()
        assertThat(state.showing).isFalse()
    }

    @Test
    fun `clearing what's already clear loads nothing`() {
        assertThat(SidebarPageState().show(null)).isEqualTo(Change.NONE)
    }

    @Test
    fun `a failure with no page asked for is nobody's`() {
        val state = SidebarPageState()
        assertThat(state.failed(404)).isFalse()
        assertThat(state.failed).isFalse()
    }

    @Test
    fun `a renderer gone loads the page again as the drawer opens`() {
        val state = drawn()
        val before = state.document
        state.rendererGone()
        assertThat(state.showing).isFalse()
        assertThat(state.ready(before, page)).isFalse()
        assertThat(state.retry()).isTrue()
    }

    @Test
    fun `an error belongs only to the page asked for last`() {
        val state = SidebarPageState()
        assertThat(state.isCurrent(page)).isFalse()
        state.show(page)
        assertThat(state.isCurrent(page)).isTrue()
        // Another organization's page asked for while the first was still loading: the first's 404 isn't its.
        state.show(other)
        assertThat(state.isCurrent(page)).isFalse()
        assertThat(state.isCurrent(other)).isTrue()
        assertThat(state.isCurrent(null)).isFalse()
        state.show(null)
        assertThat(state.isCurrent(other)).isFalse()
    }
}
