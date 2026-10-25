package com.chatwithwork.app.main

import android.content.Intent
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.push.Push
import com.chatwithwork.app.routing.AppUrls
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tapping a notification: the push's path becomes the notification's
 * ACTION_VIEW intent, MainActivity takes the link from it, and the chat
 * replaces the root (or, on a cold start, is where the navigator starts).
 */
class NotificationTapTest {
    private val base = AppConfig.baseUrl
    private val path = "/482139075/chats/42"
    private val flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

    private fun tap(data: String?, flags: Int = this.flags, restored: Boolean = false) =
        LaunchLinks.from(Intent.ACTION_VIEW, data, flags, restored)

    @Test
    fun `a tap opens the push's chat as the root`() {
        val push = Push.from(mapOf("kind" to "approval_waiting", "path" to path))!!
        val link = tap(push.url)

        assertThat(link).isEqualTo("$base$path")
        assertThat(AppUrls.isChat(link!!)).isTrue()
    }

    @Test
    fun `pushes without a path, or with a path to another host, never make a notification`() {
        assertThat(Push.from(mapOf("kind" to "approval_waiting"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to ""))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "//evil.example/chats/42"))).isNull()
        assertThat(Push.from(mapOf("kind" to "approval_waiting", "path" to "chats/42"))).isNull()
    }

    @Test
    fun `intents without a link of ours open nothing`() {
        assertThat(tap(null)).isNull()
        assertThat(tap("https://evil.example$path")).isNull()
        assertThat(tap("//evil.example$path")).isNull()
        assertThat(tap("not a url")).isNull()
        assertThat(LaunchLinks.from(Intent.ACTION_MAIN, "$base$path", 0, restored = false)).isNull()
        assertThat(LaunchLinks.from(null, "$base$path", 0, restored = false)).isNull()
    }

    @Test
    fun `the end of a browser sign-in still comes through`() {
        assertThat(tap("chatwithwork://sign-in?code=abc")).isEqualTo("chatwithwork://sign-in?code=abc")
    }

    @Test
    fun `an intent that already opened its link doesn't open it again`() {
        // The activity recreated from saved state (the process ended in the background).
        assertThat(tap("$base$path", restored = true)).isNull()
        // Relaunched from Recents with the notification's intent.
        assertThat(tap("$base$path", flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).isNull()
    }

    @Test
    fun `a link that isn't a chat waits for the navigator`() {
        val viewModel = MainViewModel()
        viewModel.pendingLink = "$base/482139075/bots"

        assertThat(viewModel.takePendingLink(navigatorReady = false)).isNull()
        assertThat(viewModel.takePendingLink(navigatorReady = true)).isEqualTo("$base/482139075/bots")
        assertThat(viewModel.takePendingLink(navigatorReady = true)).isNull()
    }
}
