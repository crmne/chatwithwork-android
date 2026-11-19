package com.chatwithwork.app.composer

import android.os.Bundle
import com.chatwithwork.app.routing.AppUrls

/**
 * What was typed in New chat and not sent, per organization. New chat is a
 * modal that the close button or back puts away, and the web keeps no draft
 * of a chat that doesn't exist yet, so the native field's words wait here
 * and come back the next time New chat opens in that organization (a
 * project's New chat too). Sending clears them. They last as long as the
 * process, and through its death in the activity's saved state, and go
 * when the person signs out.
 */
object NewChatDrafts {
    private val drafts = mutableMapOf<String, String>()

    /** Keeps [text] as New chat's draft in [location]'s organization, or forgets it when there's nothing. */
    fun keep(location: String, text: String) {
        if (!AppUrls.isNewChat(location)) return
        if (text.isBlank()) drafts.remove(key(location)) else drafts[key(location)] = text
    }

    /** The draft for [location]'s organization, taken: it's in the field from now on. */
    fun take(location: String): String? = if (AppUrls.isNewChat(location)) drafts.remove(key(location)) else null

    /** Sent: nothing waits any more. */
    fun clear(location: String) {
        if (AppUrls.isNewChat(location)) drafts.remove(key(location))
    }

    /** Signed out: every organization's draft goes. */
    fun clearAll() = drafts.clear()

    /**
     * What the field should say once [draft] comes back to it holding
     * [current] (words a share put in the page's composer): the draft, then
     * the shared words, as iOS joins them; null to leave it as it is.
     */
    fun joined(draft: String?, current: String): String? = when {
        draft.isNullOrBlank() -> null
        current.isBlank() -> draft
        current.contains(draft) -> null
        draft.contains(current) -> draft
        else -> draft.trimEnd() + "\n\n" + current.trimStart()
    }

    fun save(outState: Bundle) {
        if (drafts.isNotEmpty()) outState.putBundle(STATE_KEY, Bundle().apply { drafts.forEach(::putString) })
    }

    /** After the process ended: what it had kept, unless this process already keeps some. */
    fun restore(savedState: Bundle?) {
        val saved = savedState?.getBundle(STATE_KEY) ?: return
        if (drafts.isNotEmpty()) return
        saved.keySet().forEach { key -> saved.getString(key)?.let { drafts[key] = it } }
    }

    private fun key(location: String) = AppUrls.account(location).orEmpty()

    private const val STATE_KEY = "new_chat_drafts"
}
