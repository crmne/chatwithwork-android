package com.chatwithwork.app.fragments

import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkCostTest {
    @Test
    fun `wifi without Data Saver costs nothing`() {
        val value = NetworkCost.value(notMetered = true, RESTRICT_BACKGROUND_STATUS_DISABLED)
        assertThat(value).isEqualTo(NetworkCost.Value(metered = false, constrained = false))
        assertThat(value!!.free).isTrue()
    }

    @Test
    fun `mobile data is metered, and Data Saver constrains`() {
        assertThat(NetworkCost.value(notMetered = false, RESTRICT_BACKGROUND_STATUS_DISABLED))
            .isEqualTo(NetworkCost.Value(metered = true, constrained = false))
        assertThat(NetworkCost.value(notMetered = true, RESTRICT_BACKGROUND_STATUS_ENABLED))
            .isEqualTo(NetworkCost.Value(metered = false, constrained = true))
        // Data Saver on, with this app allowed to use data in the background anyway.
        assertThat(NetworkCost.value(notMetered = true, RESTRICT_BACKGROUND_STATUS_WHITELISTED)!!.constrained).isFalse()
    }

    @Test
    fun `no network says nothing`() {
        assertThat(NetworkCost.value(notMetered = null, RESTRICT_BACKGROUND_STATUS_DISABLED)).isNull()
    }

    @Test
    fun `the page gets the value, and asks to preload only when told to`() {
        val free = NetworkCost.Value(metered = false, constrained = false)
        assertThat(NetworkCost.script(free, preload = false))
            .isEqualTo("window.chatWithWorkNetwork = { metered: false, constrained: false };")
        assertThat(NetworkCost.script(free, preload = true)).contains("window.chatWithWork.preload()")
    }
}
