package com.chatwithwork.app.main

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainTabTest {
    private val base = AppConfig.baseUrl

    @Test
    fun `tabs start on unprefixed pages the server redirects into the last organization`() {
        assertThat(MainTab.CHATS.configuration.startLocation).isEqualTo("$base/chats")
        assertThat(MainTab.BOTS.configuration.startLocation).isEqualTo("$base/bots")
        assertThat(MainTab.SETTINGS.configuration.startLocation).isEqualTo("$base/settings")
        assertThat(MainTab.entries.map { it.configuration.name })
            .containsExactly("chats", "bots", "settings")
            .inOrder()
    }

    @Test
    fun `after moving to another organization, tabs start inside it`() {
        MainTab.CHATS.configuration("2000002").let {
            assertThat(it.startLocation).isEqualTo("$base/2000002/chats")
            // Same navigator: only where it starts changes.
            assertThat(it.name).isEqualTo(MainTab.CHATS.configuration.name)
            assertThat(it.navigatorHostId).isEqualTo(MainTab.CHATS.configuration.navigatorHostId)
        }
        assertThat(MainTab.SETTINGS.configuration("2000002").startLocation).isEqualTo("$base/2000002/settings")
    }

    @Test
    fun `links open in the tab they belong to`() {
        assertThat(MainTab.forUrl("$base/482139075/chats/42")).isEqualTo(MainTab.CHATS)
        assertThat(MainTab.forUrl("$base/482139075/projects/3")).isEqualTo(MainTab.CHATS)
        assertThat(MainTab.forUrl("$base/482139075/bots/7")).isEqualTo(MainTab.BOTS)
        assertThat(MainTab.forUrl("$base/482139075/settings?tab=connectors")).isEqualTo(MainTab.SETTINGS)
        assertThat(MainTab.forUrl("$base/accounts")).isNull()
    }

    @Test
    fun `pages name the tabs to show, and guests get no Bots`() {
        assertThat(MainTab.hiddenBy("chats bots settings")).isEmpty()
        assertThat(MainTab.hiddenBy("chats settings")).containsExactly(MainTab.BOTS)
        // Chats and Settings always show.
        assertThat(MainTab.hiddenBy("bots")).containsExactly()
        assertThat(MainTab.hiddenBy("")).containsExactly(MainTab.BOTS)
    }
}
