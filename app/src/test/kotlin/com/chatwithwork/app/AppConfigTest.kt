package com.chatwithwork.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppConfigTest {
    @Test
    fun `introduces the app the way the server reads it`() {
        val prefix = AppConfig.userAgentPrefix(versionName = "1.2.3", versionCode = 10203)

        assertThat(prefix).isEqualTo("Chat with Work; platform=android; version=1.2.3; build=10203;")

        // The server's parser (docs/server-contract.md) and the iOS app's prefix
        // share this shape, with platform=ios there.
        val parsed = Regex("Chat with Work; platform=(\\w+); version=([^;]+); build=([^;]+);").find(prefix)
        assertThat(parsed?.groupValues?.drop(1)).containsExactly("android", "1.2.3", "10203").inOrder()
    }

    @Test
    fun `fetches the versioned Android path configuration from its own server`() {
        assertThat(
            AppConfig.remotePathConfigurationUrl
        ).isEqualTo("${AppConfig.baseUrl}/configurations/android_v1.json")
        assertThat(AppConfig.baseUrl).doesNotMatch(".*/$")
    }
}
