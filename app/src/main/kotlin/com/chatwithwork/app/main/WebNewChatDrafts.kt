package com.chatwithwork.app.main

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Words typed in New chat's page composer and not sent, per organization,
 * so back goes to a New chat that has some and skips an empty one. The page
 * keeps them (`chat_controller.js`, in the web view's localStorage under
 * `chat-with-work:draft:<key>`), its key `new_chat_<the organization's own
 * id>`, which isn't the id in the app's paths: the app learns each
 * organization's key from a page that carries its New chat composer (New
 * chat itself, Decisions' All clear), and reads which keys have words after
 * every visit. NewChatDrafts holds the native composer's.
 */
class WebNewChatDrafts {
    private val keys = mutableMapOf<String, String>()
    private var drafted = emptySet<String>()

    /** What [SCRIPT] answered on a page of [account]. */
    fun read(account: String?, answer: String?) {
        val reading = parse(answer) ?: return
        if (account != null && reading.key != null) keys[account] = reading.key
        drafted = reading.drafted
    }

    /** Whether New chat in [account] keeps words; false while the app doesn't know its key. */
    fun has(account: String?): Boolean = keys[account]?.let { it in drafted } ?: false

    data class Reading(val key: String?, val drafted: Set<String>)

    companion object {
        private const val PREFIX = "chat-with-work:draft:"

        /**
         * The page's own New chat key, if it carries that composer, and every
         * New chat key with words, as JSON: `{"key": "new_chat_7", "drafted":
         * ["new_chat_7"]}`.
         */
        const val SCRIPT =
            "(() => { const key = document.querySelector(\"[data-chat-draft-key-value^='new_chat_']\")" +
                "?.dataset.chatDraftKeyValue || null; let drafted = []; try { drafted = " +
                "Object.keys(window.localStorage).filter(k => k.startsWith('$PREFIX' + 'new_chat_') && " +
                "(window.localStorage.getItem(k) || '').trim() !== '').map(k => k.slice(${PREFIX.length})) " +
                "} catch (_) {} return JSON.stringify({ key, drafted }) })()"

        /** evaluateJavascript's answer: the script's JSON string, itself JSON-encoded. */
        fun parse(answer: String?): Reading? = runCatching {
            val inner = (Json.parseToJsonElement(answer ?: return null) as? JsonPrimitive)?.takeIf { it.isString }
                ?: return null
            val reading = Json.parseToJsonElement(inner.content).jsonObject
            val key = (reading["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val drafted =
                reading["drafted"]?.jsonArray.orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    .toSet()
            Reading(key, drafted)
        }.getOrNull()
    }
}
