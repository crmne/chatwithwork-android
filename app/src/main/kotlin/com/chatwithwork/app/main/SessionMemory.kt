package com.chatwithwork.app.main

import android.content.Context
import androidx.core.content.edit

/**
 * The little the app remembers between launches about the session the web
 * view holds: whether someone signed in, which organization they were in,
 * what the sidebar showed there (and so where New chat starts), whether it
 * showed chats or bots, and the chat they left while it was still
 * answering. The session itself is the server's cookie in the web view's
 * cookie store; this only decides what to show before the first page
 * answers.
 */
class SessionMemory(context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var hasSignedIn: Boolean
        get() = preferences.getBoolean(KEY_SIGNED_IN, false)
        set(value) = preferences.edit { putBoolean(KEY_SIGNED_IN, value) }

    var lastAccount: String?
        get() = preferences.getString(KEY_ACCOUNT, null)
        set(value) = preferences.edit { putString(KEY_ACCOUNT, value) }

    /** The list the sidebar shows in [account] ("recent", "all", or a project's number). */
    fun listKey(account: String): String? = preferences.getString("$KEY_LIST$account", null)

    fun setListKey(account: String, key: String?) = preferences.edit { putString("$KEY_LIST$account", key) }

    /** The half of the sidebar's Chats | Bots switch last chosen, on this install ("chats" or "bots"). */
    var sidebarView: String?
        get() = preferences.getString(KEY_VIEW, null)
        set(value) = preferences.edit { putString(KEY_VIEW, value) }

    /** Where New chat starts in [account], as the sidebar last said ("/482139075/chats/new?project_id=3"). */
    fun newChatPath(account: String): String? = preferences.getString("$KEY_NEW_CHAT$account", null)

    fun setNewChatPath(account: String, path: String?) = preferences.edit { putString("$KEY_NEW_CHAT$account", path) }

    /** The chat the person left while it answered, and when, to reopen it on a launch soon after. */
    fun leftAnswering(url: String?, at: Long) = preferences.edit {
        putString(KEY_LEFT_CHAT, url)
        putLong(KEY_LEFT_AT, at)
    }

    fun answeringChatLeftSince(now: Long, within: Long): String? {
        val url = preferences.getString(KEY_LEFT_CHAT, null) ?: return null
        val at = preferences.getLong(KEY_LEFT_AT, 0)
        return url.takeIf { now - at in 0..within }
    }

    private companion object {
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_ACCOUNT = "account"
        const val KEY_LIST = "list:"
        const val KEY_NEW_CHAT = "new_chat:"
        const val KEY_VIEW = "sidebar_view"
        const val KEY_LEFT_CHAT = "left_answering_chat"
        const val KEY_LEFT_AT = "left_answering_at"
    }
}
