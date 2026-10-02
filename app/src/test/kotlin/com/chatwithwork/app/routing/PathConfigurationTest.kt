package com.chatwithwork.app.routing

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.gson.Gson
import dev.hotwire.core.turbo.config.PathConfigurationData
import dev.hotwire.core.turbo.config.PathConfigurationProperties
import java.io.File
import org.junit.Test

/**
 * The bundled path configuration, read the way Hotwire Native reads it. The
 * server's android_v1.json must give the same answers (docs/server-contract.md).
 */
class PathConfigurationTest {
    private val json = File("src/main/assets/json/path-configuration.json").readText()
    private val configuration: PathConfigurationData = Gson().fromJson(json, PathConfigurationData::class.java)
    private val base = AppConfig.baseUrl

    private fun properties(path: String): PathConfigurationProperties = configuration.properties("$base$path")

    @Test
    fun `every pattern compiles`() {
        Regex("\"patterns\":\\s*\\[([^\\]]*)]")
            .findAll(json)
            .flatMap { match ->
                Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(match.groupValues[1]).map {
                    it.groupValues[1].replace("\\\\", "\\")
                }
            }.forEach { pattern -> Regex(pattern) }
    }

    @Test
    fun `pages are web screens in their tab with pull to refresh`() {
        properties("/482139075/chats").let {
            assertThat(it["context"]).isEqualTo("default")
            assertThat(it["uri"]).isEqualTo("hotwire://fragment/web")
            assertThat(it["pull_to_refresh_enabled"]).isEqualTo(true)
        }
        assertThat(properties("/projects")["pull_to_refresh_enabled"]).isEqualTo(true)
        assertThat(properties("/482139075/projects/3")["context"]).isEqualTo("default")
    }

    @Test
    fun `a chat scrolls its conversation instead of refreshing`() {
        properties("/482139075/chats/42").let {
            assertThat(it["context"]).isEqualTo("default")
            assertThat(it["pull_to_refresh_enabled"]).isEqualTo(false)
        }
        assertThat(properties("/482139075/chats/42#tool_call_7")["pull_to_refresh_enabled"]).isEqualTo(false)
        assertThat(properties("/shared/abc123")["pull_to_refresh_enabled"]).isEqualTo(false)
    }

    @Test
    fun `new chat and forms are full-screen modals`() {
        listOf(
            "/482139075/chats/new",
            "/chats/new?project_id=3",
            "/482139075/chats/42/edit",
            "/482139075/projects/new",
            "/482139075/projects/3/edit"
        ).forEach { path ->
            properties(path).let {
                assertWithMessage(path).that(it["context"]).isEqualTo("modal")
                assertWithMessage(path).that(it["uri"]).isEqualTo("hotwire://fragment/web/modal")
                assertWithMessage(path).that(it["pull_to_refresh_enabled"]).isEqualTo(false)
            }
        }
    }

    @Test
    fun `sign-in pages are modals`() {
        listOf(
            "/users/sign_in",
            "/users/sign_up",
            "/users",
            "/users/password/new",
            "/users/password/edit?reset_password_token=abc",
            "/users/confirmation/sent"
        ).forEach { path ->
            assertWithMessage(path).that(properties(path)["context"]).isEqualTo("modal")
        }
    }

    @Test
    fun `a settings section is pushed over the list of sections`() {
        // Hotwire Native pushes a location whose query differs, as iOS does.
        properties("/482139075/settings?tab=connectors").let {
            assertThat(it["context"]).isEqualTo("default")
            assertThat(it["query_string_presentation"]).isNull()
        }
    }

    @Test
    fun `files and marketing pages open in the browser`() {
        listOf(
            "/rails/active_storage/blobs/redirect/abc/report.pdf",
            "/",
            "/pricing",
            "/privacy-policy",
            "/terms-of-service",
            "/integrations/slack",
            "/compare/chatgpt"
        ).forEach { path ->
            properties(path).let {
                assertWithMessage(path).that(it["open_in_browser"]).isEqualTo(true)
                assertWithMessage(path).that(it["presentation"]).isEqualTo("none")
            }
        }

        assertThat(properties("/482139075/settings")["open_in_browser"]).isNull()
        assertThat(properties("/accounts")["open_in_browser"]).isNull()
    }

    @Test
    fun `Turbo's historical locations keep their built-in rules`() {
        assertThat(properties("/recede_historical_location")["presentation"]).isEqualTo("POP")
        assertThat(properties("/refresh_historical_location")["presentation"]).isEqualTo("REFRESH")
    }
}
