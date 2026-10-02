package com.chatwithwork.app.routing

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AppUrlsTest {
    private val base = AppConfig.baseUrl

    @Test
    fun `recognizes this server's pages and nothing else`() {
        assertThat(AppUrls.isAppUrl("$base/482139075/chats/42")).isTrue()
        assertThat(AppUrls.isAppUrl("$base/")).isTrue()
        assertThat(AppUrls.isAppUrl("https://accounts.google.com/o/oauth2/auth")).isFalse()
        assertThat(AppUrls.isAppUrl("https://chatwithwork.com.evil.example/chats")).isFalse()
        assertThat(AppUrls.isAppUrl("mailto:support@chatwithwork.com")).isFalse()
        assertThat(AppUrls.isAppUrl("not a url")).isFalse()
    }

    @Test
    fun `a different port is a different server`() {
        val other = base.replace(Regex(":\\d+$"), "") + ":4000"
        assertThat(AppUrls.isAppUrl("$other/chats")).isFalse()
    }

    @Test
    fun `reads the organization from the first path segment`() {
        assertThat(AppUrls.account("$base/482139075/chats/42")).isEqualTo("482139075")
        assertThat(AppUrls.account("$base/482139075")).isEqualTo("482139075")
        assertThat(AppUrls.account("$base/chats/42")).isNull()
        // Rails' AccountSlug pattern wants seven digits or more.
        assertThat(AppUrls.account("$base/123456/chats")).isNull()
    }

    @Test
    fun `strips the organization from paths`() {
        assertThat(AppUrls.pathWithinAccount("$base/482139075/chats/42")).isEqualTo("/chats/42")
        assertThat(AppUrls.pathWithinAccount("$base/482139075")).isEqualTo("/")
        assertThat(AppUrls.pathWithinAccount("$base/settings?tab=connectors")).isEqualTo("/settings")
    }

    @Test
    fun `puts links in the tab they belong to`() {
        assertThat(AppUrls.section("$base/482139075/chats/42")).isEqualTo(AppUrls.Section.CHATS)
        assertThat(AppUrls.section("$base/projects")).isEqualTo(AppUrls.Section.PROJECTS)
        assertThat(AppUrls.section("$base/482139075/settings?tab=billing")).isEqualTo(AppUrls.Section.SETTINGS)
        assertThat(AppUrls.section("$base/accounts")).isEqualTo(AppUrls.Section.OTHER)
        assertThat(AppUrls.section("$base/chatsroom")).isEqualTo(AppUrls.Section.OTHER)
    }

    @Test
    fun `knows tab roots and the new chat page`() {
        assertThat(AppUrls.isTabRoot("$base/482139075/chats")).isTrue()
        assertThat(AppUrls.isTabRoot("$base/settings?tab=connectors")).isTrue()
        assertThat(AppUrls.isTabRoot("$base/482139075/chats/42")).isFalse()
        assertThat(AppUrls.isNewChat("$base/482139075/chats/new?project_id=3")).isTrue()
        assertThat(AppUrls.isNewChat("$base/482139075/chats/42")).isFalse()
    }

    @Test
    fun `knows the sign-in pages`() {
        listOf(
            "/users/sign_in",
            "/users/sign_up",
            "/users",
            "/users/password/new",
            "/users/password/edit?reset_password_token=abc",
            "/users/confirmation?confirmation_token=abc",
            "/users/confirmation/sent",
            "/users/auth/google_oauth2/callback",
            "/native/sign_in?token=abc"
        ).forEach { assertWithMessage(it).that(AppUrls.isAuthentication("$base$it")).isTrue() }

        listOf("/users/edit", "/482139075/chats", "/accounts", "/").forEach {
            assertWithMessage(it).that(AppUrls.isAuthentication("$base$it")).isFalse()
        }

        assertThat(AppUrls.isSignInForm("$base/users/sign_in")).isTrue()
        assertThat(AppUrls.isSignInForm("$base/users/sign_up")).isFalse()
    }

    @Test
    fun `knows Turbo's historical locations, files, and the home page`() {
        assertThat(AppUrls.isHistoricalLocation("$base/recede_historical_location?notice=Saved")).isTrue()
        assertThat(AppUrls.isHistoricalLocation("$base/refresh_historical_location")).isTrue()
        assertThat(AppUrls.isHistoricalLocation("$base/chats")).isFalse()
        assertThat(AppUrls.isRecede("$base/recede_historical_location?notice=Chat+renamed.")).isTrue()
        assertThat(AppUrls.isRecede("$base/refresh_historical_location")).isFalse()
        assertThat(AppUrls.isHistoricalLocation(AppUrls.resume)).isTrue()
        assertThat(AppUrls.isFile("$base/rails/active_storage/blobs/redirect/abc/report.pdf")).isTrue()
        assertThat(AppUrls.isHome("$base/")).isTrue()
        assertThat(AppUrls.isHome(base)).isTrue()
        assertThat(AppUrls.isHome("$base/chats")).isFalse()
    }

    @Test
    fun `recognizes the browser sign-in callback`() {
        assertThat(AppUrls.isAuthCallback("chatwithwork://sign-in?token=abc")).isTrue()
        assertThat(AppUrls.isAuthCallback("chatwithwork://handoff?status=connected&return_to=%2Fsettings")).isTrue()
        assertThat(AppUrls.isAuthCallback("https://chatwithwork.com/sign-in?token=abc")).isFalse()
    }

    @Test
    fun `builds the new chat page inside the organization when known`() {
        assertThat(AppUrls.newChat("482139075")).isEqualTo("$base/482139075/chats/new")
        assertThat(AppUrls.newChat(null)).isEqualTo("$base/chats/new")
    }
}
