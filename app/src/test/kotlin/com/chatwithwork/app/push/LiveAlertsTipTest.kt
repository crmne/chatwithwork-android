package com.chatwithwork.app.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveAlertsTipTest {
    @Test
    fun `only OnePlus and OPPO on Android 16 have the Live Alerts switch`() {
        assertThat(LiveAlertsTip.applies("OnePlus", 36)).isTrue()
        assertThat(LiveAlertsTip.applies("OPPO", 36)).isTrue()
        assertThat(LiveAlertsTip.applies("oneplus ", 37)).isTrue()
    }

    @Test
    fun `phones without such a switch never get the tip`() {
        assertThat(LiveAlertsTip.applies("OnePlus", 35)).isFalse()
        assertThat(LiveAlertsTip.applies("Google", 36)).isFalse()
        assertThat(LiveAlertsTip.applies("samsung", 36)).isFalse()
        assertThat(LiveAlertsTip.applies("Xiaomi", 36)).isFalse()
    }
}
