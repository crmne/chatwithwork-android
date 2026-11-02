package com.chatwithwork.app.fragments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageScriptsTest {
    @Test
    fun `gives the page the bar's height in its own pixels`() {
        assertThat(PageScripts.topBarHeightScript(280, 1080))
            .isEqualTo(
                "document.documentElement.style" +
                    ".setProperty('--native-top-bar-height', (280 * window.innerWidth / 1080) + 'px')"
            )
    }
}
