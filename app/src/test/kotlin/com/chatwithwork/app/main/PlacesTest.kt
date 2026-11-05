package com.chatwithwork.app.main

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertWithMessage
import com.google.gson.Gson
import dev.hotwire.core.turbo.config.PathConfigurationData
import java.io.File
import org.junit.Test

/** What the sidebar opens, with the bundled path configuration: a place at the root, or a form over the screen. */
class PlacesTest {
    private val configuration: PathConfigurationData =
        Gson().fromJson(
            File("src/main/assets/json/path-configuration.json").readText(),
            PathConfigurationData::class.java
        )

    private fun opening(path: String) = Places.opening(configuration.properties("${AppConfig.baseUrl}$path"))

    @Test
    fun `a place the configuration replaces the root with is routed as it says`() {
        listOf(
            "/482139075/chats/42",
            "/482139075/chats/new",
            "/482139075/decisions?decision=t7",
            "/482139075/bots",
            "/482139075/settings"
        )
            .forEach { assertWithMessage(it).that(opening(it)).isEqualTo(Places.Opening.ROUTE) }
    }

    @Test
    fun `a page pushed elsewhere replaces the root when the sidebar opens it`() {
        listOf("/482139075/bots/7", "/482139075/projects/3", "/482139075/settings?tab=assistant&project=3", "/accounts")
            .forEach { assertWithMessage(it).that(opening(it)).isEqualTo(Places.Opening.REPLACE_ROOT) }
    }

    @Test
    fun `forms and pickers come up as modals over the screen`() {
        listOf(
            "/482139075/projects/new",
            "/482139075/chats/42/edit",
            "/482139075/chats/42/section/edit",
            "/482139075/bots/new"
        )
            .forEach { assertWithMessage(it).that(opening(it)).isEqualTo(Places.Opening.ROUTE) }
    }
}
