package com.chatwithwork.app.bridge

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** What the app announces in its user agent (`bridge-components`), which decides which composer the page shows. */
class BridgeComponentsTest {
    @Test
    fun `without the native composer, composer is left out and the page shows its own`() {
        val names = BridgeComponents.registered(nativeComposer = false).map { it.name }
        assertThat(names).doesNotContain("composer")
        assertThat(names).containsAtLeast("button", "menu", "form", "sidebar", "notification-token")
    }

    @Test
    fun `with it, composer is announced and the page hides its own`() {
        assertThat(BridgeComponents.registered(nativeComposer = true).map { it.name }).contains("composer")
    }

    @Test
    fun `the build registers what its switch says`() {
        assertThat(BridgeComponents.all.map { it.name })
            .isEqualTo(BridgeComponents.registered(AppConfig.NATIVE_COMPOSER).map { it.name })
    }
}
