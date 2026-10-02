package com.chatwithwork.app.fragments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageTitlesTest {
    @Test
    fun `keeps the page's own name`() {
        assertThat(PageTitles.clean("Settings")).isEqualTo("Settings")
        assertThat(PageTitles.clean("Q3 planning")).isEqualTo("Q3 planning")
    }

    @Test
    fun `drops the browser tab's extras`() {
        // Unit tests run the debug build, which isn't production.
        assertThat(PageTitles.clean("Staging · Sign in to your account")).isEqualTo("Sign in to your account")
        assertThat(PageTitles.clean("AI Knowledge Base | Chat with Work")).isEqualTo("AI Knowledge Base")
        assertThat(PageTitles.clean(null)).isEmpty()
    }

    @Test
    fun `leaves names that only contain the words alone`() {
        assertThat(PageTitles.clean("Chat with Work")).isEqualTo("Chat with Work")
        assertThat(PageTitles.clean("Staging plan · draft")).isEqualTo("Staging plan · draft")
    }
}
