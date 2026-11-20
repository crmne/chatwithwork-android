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
    fun `the app opens on the organization's home, or the unprefixed one before it knows the organization`() {
        assertThat(AppUrls.home("482139075")).isEqualTo("$base/482139075/home")
        assertThat(AppUrls.home(null)).isEqualTo("$base/home")
    }

    @Test
    fun `a server without a home sends the app to its chat list, a place`() {
        assertThat(AppUrls.homeMissing("$base/482139075/home", 404)).isTrue()
        assertThat(AppUrls.homeMissing("$base/home", 404)).isTrue()
        assertThat(AppUrls.homeMissing("$base/home?x=1", 404)).isTrue()
        // Any other failure, or any other page, is the error screen as before.
        assertThat(AppUrls.homeMissing("$base/482139075/home", 500)).isFalse()
        assertThat(AppUrls.homeMissing("$base/482139075/home", null)).isFalse()
        assertThat(AppUrls.homeMissing("$base/482139075/chats/42", 404)).isFalse()
        assertThat(AppUrls.homeMissing("$base/482139075/bots/home", 404)).isFalse()
        assertThat(AppUrls.homeMissing("https://chatwithwork.example/home", 404)).isFalse()
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
    fun `knows the chats that replace the root`() {
        assertThat(AppUrls.isChat("$base/482139075/chats/42")).isTrue()
        assertThat(AppUrls.isChat("$base/482139075/chats/42?x=1#tool_call_7")).isTrue()
        assertThat(AppUrls.isChat("$base/482139075/chats/new?project_id=3")).isTrue()
        assertThat(AppUrls.isChat("$base/chats/new")).isTrue()
        assertThat(AppUrls.isChat("$base/482139075/chats")).isFalse()
        assertThat(AppUrls.isChat("$base/482139075/chats/42/edit")).isFalse()
        assertThat(AppUrls.isChat("$base/482139075/bots")).isFalse()
        assertThat(AppUrls.isNewChat("$base/482139075/chats/new?project_id=3")).isTrue()
        assertThat(AppUrls.isNewChat("$base/482139075/chats/42")).isFalse()
        assertThat(AppUrls.isOpenChat("$base/482139075/chats/42")).isTrue()
        assertThat(AppUrls.isOpenChat("$base/482139075/chats/new")).isFalse()
        assertThat(AppUrls.isOpenChat("$base/482139075/chats/new?project_id=3")).isFalse()
        assertThat(AppUrls.chats("482139075")).isEqualTo("$base/482139075/chats")
    }

    @Test
    fun `knows New chat and New bot, which open as places from outside and the sidebar`() {
        assertThat(AppUrls.opensAsPlace("$base/482139075/chats/new")).isTrue()
        assertThat(AppUrls.opensAsPlace("$base/482139075/chats/new?text=hi&title=Q3")).isTrue()
        assertThat(AppUrls.opensAsPlace("$base/482139075/bots/new")).isTrue()
        assertThat(AppUrls.opensAsPlace("$base/482139075/bots/new?recipe=triage")).isTrue()
        assertThat(AppUrls.isNewBot("$base/482139075/bots/new")).isTrue()
        assertThat(AppUrls.isNewBot("$base/482139075/bots/7")).isFalse()
        assertThat(AppUrls.opensAsPlace("$base/482139075/projects/new")).isFalse()
        assertThat(AppUrls.opensAsPlace("$base/482139075/chats/42")).isFalse()
        assertThat(AppUrls.opensAsPlace("https://example.com/482139075/chats/new")).isFalse()
    }

    @Test
    fun `knows the pages whose composer turns into the chat, and a chat that's a place`() {
        listOf(
            "/482139075/chats/new",
            "/482139075/chats/new?project_id=3",
            "/482139075/bots/new",
            "/home",
            "/482139075/home",
            "/482139075/decisions"
        )
            .forEach { assertWithMessage(it).that(AppUrls.turnsIntoChat("$base$it")).isTrue() }
        listOf("/482139075/chats/42", "/482139075/bots", "/482139075/bots/7", "/482139075/chats/42/edit")
            .forEach { assertWithMessage(it).that(AppUrls.turnsIntoChat("$base$it")).isFalse() }
        assertThat(AppUrls.isChatPlace("$base/482139075/chats/42")).isTrue()
        assertThat(AppUrls.isChatPlace("$base/482139075/chats/42?x=1")).isTrue()
        assertThat(AppUrls.isChatPlace("$base/482139075/chats/42?from=decisions")).isFalse()
        assertThat(AppUrls.isChatPlace("$base/482139075/chats/42?x=1&from=bot")).isFalse()
        assertThat(AppUrls.isChatPlace("$base/482139075/chats/new")).isFalse()
    }

    @Test
    fun `reads a chat's number, and its push thread for a Live Update`() {
        assertThat(AppUrls.chatNumber("$base/482139075/chats/42")).isEqualTo(42L)
        assertThat(AppUrls.chatNumber("$base/482139075/chats/new")).isNull()
        assertThat(AppUrls.chatNumber("$base/482139075/bots")).isNull()
        assertThat(com.chatwithwork.app.push.LiveUpdates.threadFor("$base/482139075/chats/42"))
            .isEqualTo("chat-482139075-42")
        assertThat(com.chatwithwork.app.push.LiveUpdates.threadFor("$base/chats/42")).isNull()
    }

    @Test
    fun `builds the sidebar's address and the server's paths`() {
        assertThat(AppUrls.sidebar("482139075")).isEqualTo("$base/482139075/native/sidebar")
        assertThat(AppUrls.sidebar("482139075", "3")).isEqualTo("$base/482139075/native/sidebar?list=3")
        assertThat(AppUrls.sidebar("482139075", "recent", all = true))
            .isEqualTo("$base/482139075/native/sidebar?list=recent&all=1")
        assertThat(AppUrls.absolute("/482139075/chats/42")).isEqualTo("$base/482139075/chats/42")
        assertThat(AppUrls.absolute("//evil.example/x")).isEqualTo("//evil.example/x")
        assertThat(AppUrls.newChat("482139075", "3")).isEqualTo("$base/482139075/chats/new?project_id=3")
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
    fun `reads the notice or alert a historical location carries`() {
        assertThat(AppUrls.historicalNotice("$base/482139075/recede_historical_location?notice=Chat+renamed."))
            .isEqualTo(AppUrls.Notice("Chat renamed.", long = false))
        assertThat(AppUrls.historicalNotice("$base/refresh_historical_location?alert=Couldn%E2%80%99t+move+it"))
            .isEqualTo(AppUrls.Notice("Couldn\u2019t move it", long = true))
        assertThat(AppUrls.historicalNotice("$base/resume_historical_location?notice=&alert=No"))
            .isEqualTo(AppUrls.Notice("No", long = true))
        assertThat(AppUrls.historicalNotice("$base/recede_historical_location")).isNull()
        assertThat(AppUrls.historicalNotice("$base/recede_historical_location?notice=")).isNull()
        assertThat(AppUrls.historicalNotice("$base/482139075/chats/42?notice=Hi")).isNull()
    }

    @Test
    fun `knows Turbo's historical locations, files, and the home page`() {
        assertThat(AppUrls.isHistoricalLocation("$base/recede_historical_location?notice=Saved")).isTrue()
        assertThat(AppUrls.isHistoricalLocation("$base/refresh_historical_location")).isTrue()
        assertThat(AppUrls.isHistoricalLocation("$base/chats")).isFalse()
        assertThat(AppUrls.isRecede("$base/recede_historical_location?notice=Chat+renamed.")).isTrue()
        assertThat(AppUrls.isRecede("$base/refresh_historical_location")).isFalse()
        assertThat(AppUrls.isHistoricalLocation(AppUrls.resume)).isTrue()
        // Account-scoped controllers send them under the organization's prefix.
        assertThat(AppUrls.isHistoricalLocation("$base/482139075/recede_historical_location?notice=Moved")).isTrue()
        assertThat(AppUrls.isRecede("$base/482139075/recede_historical_location")).isTrue()
        assertThat(AppUrls.isRecede("$base/482139075/refresh_historical_location")).isFalse()
        assertThat(AppUrls.isHistoricalLocation("https://chatwithwork.example/recede_historical_location")).isFalse()
        assertThat(AppUrls.isFile("$base/rails/active_storage/blobs/redirect/abc/report.pdf")).isTrue()
        assertThat(AppUrls.isFileViewer("$base/482139075/chats/42/files/abc")).isTrue()
        assertThat(AppUrls.isFileViewer("$base/482139075/chats/42")).isFalse()
        assertThat(AppUrls.isFileViewer("$base/rails/active_storage/blobs/redirect/abc/report.pdf")).isFalse()
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

    @Test
    fun `Decisions is Decisions in any of its views, on this server only`() {
        assertThat(AppUrls.isDecisions("$base/482139075/decisions")).isTrue()
        assertThat(AppUrls.isDecisions("$base/482139075/decisions?view=now")).isTrue()
        assertThat(AppUrls.isDecisions("$base/482139075/decisions/7")).isFalse()
        assertThat(AppUrls.isDecisions("https://example.com/482139075/decisions")).isFalse()
    }
}
