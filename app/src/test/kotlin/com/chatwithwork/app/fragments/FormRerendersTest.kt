package com.chatwithwork.app.fragments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A form posted as a whole page that comes back with its mistakes stays the page (a wrong password). */
class FormRerendersTest {
    @Test
    fun `a form posted and shown again with 422 is the page, not an error`() {
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 422)).isTrue()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "PUT", status = 422)).isTrue()
    }

    @Test
    fun `anything else stays an error`() {
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "GET", status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = null, status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = false, method = "POST", status = 422)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 500)).isFalse()
        assertThat(FormRerenders.isRerender(isForMainFrame = true, method = "POST", status = 401)).isFalse()
    }
}
