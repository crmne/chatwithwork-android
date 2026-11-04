package com.chatwithwork.app.fragments

import android.webkit.WebViewClient
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineCopiesTest {
    @Test
    fun `holds only a page's own network failure, which the service worker may answer`() {
        for (code in listOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_UNKNOWN
        )) {
            assertThat(OfflineCopies.mayBeAnswered(isForMainFrame = true, errorCode = code)).isTrue()
            assertThat(OfflineCopies.mayBeAnswered(isForMainFrame = false, errorCode = code)).isFalse()
        }
        assertThat(
            OfflineCopies.mayBeAnswered(isForMainFrame = true, errorCode = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
        )
            .isFalse()
        assertThat(
            OfflineCopies.mayBeAnswered(isForMainFrame = true, errorCode = WebViewClient.ERROR_BAD_URL)
        ).isFalse()
    }

    @Test
    fun `drops it for a kept copy, gives it back for the worker's offline page, and waits past anything else`() {
        assertThat(OfflineCopies.page("\"copy\"")).isEqualTo(OfflineCopies.Page.COPY)
        assertThat(OfflineCopies.page("\"offline\"")).isEqualTo(OfflineCopies.Page.OFFLINE)
        assertThat(OfflineCopies.page("\"other\"")).isEqualTo(OfflineCopies.Page.OTHER)
        assertThat(OfflineCopies.page("null")).isEqualTo(OfflineCopies.Page.OTHER)
        assertThat(OfflineCopies.page(null)).isEqualTo(OfflineCopies.Page.OTHER)
    }
}
