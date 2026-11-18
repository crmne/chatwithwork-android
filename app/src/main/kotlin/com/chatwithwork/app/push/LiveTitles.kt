package com.chatwithwork.app.push

import android.content.Context
import androidx.core.content.edit
import com.chatwithwork.app.AppConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The headline of a chat's Live Update: its title, or in a bot's chat the
 * bot's name and then the title (docs/server-contract.md, "Live Updates").
 * It never comes in a push. Each `progress` message carries `title_ref`, an
 * id the app can't read, and the app asks the server for the title with it
 * and the phone's notification key (`GET /native/live_titles/:id`), as a
 * notification's words are fetched ([NotificationWords]).
 *
 * The answer is kept per chat (its thread), so a card shows it at once, the
 * next run of the same chat included, and the app asks again only once per
 * run and while the chat has no title yet ("New chat" until the summarized one
 * comes). Without an answer (offline, no key, an older server) the card says
 * what the step is, as it always did.
 */
object LiveTitles {
    /** `GET /native/live_titles/:id`'s answer. Empty where the person keeps words out of notifications. */
    @Serializable
    data class Title(val title: String? = null, val bot: String? = null, val titled: Boolean? = null) {
        /** Whether it says nothing, as for someone who keeps words out of notifications. */
        val isEmpty: Boolean get() = title.isNullOrBlank() && bot.isNullOrBlank()

        /**
         * The headline: the title, or the bot's name and the title after it,
         * which the system cuts short where there's no room. A chat with no title
         * yet is [newChat], a bot's run its name alone; nothing when it's empty.
         */
        fun headline(newChat: String): String? {
            val title = title?.trim()?.takeIf { it.isNotEmpty() }
            val bot = bot?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                bot != null -> listOfNotNull(bot, title).joinToString(" · ")
                title != null -> title
                titled == false -> newChat
                else -> null
            }
        }
    }

    /** What's kept for a chat: the answer, and the run it was fetched for. */
    @Serializable
    data class Kept(val run: String? = null, val title: Title = Title())

    private const val TIMEOUT_MS = 3_000

    /**
     * Whether a title asked for may still be kept: it's dropped once the
     * session ends, or once the key it was asked with is gone or replaced.
     * The session and the key are taken together, and sign-out removes the
     * key before it ends the session ([PushNotifications.forget]), so no
     * ordering of sign-out and a request under way leaves a title behind.
     */
    class Gate {
        data class Ask(val session: Int, val key: String)

        private var session = 0

        /** The session and key to ask with, or null without a key. */
        @Synchronized
        fun ask(key: () -> String?): Ask? = key()?.let { Ask(session, it) }

        /** Runs [keep] only if [ask]'s session and key are still the current ones. */
        @Synchronized
        fun keepIf(ask: Ask, key: () -> String?, keep: () -> Unit) {
            if (ask.session == session && key() == ask.key) keep()
        }

        /** The session ended: runs [forget] as it does, so nothing asked before is kept after. */
        @Synchronized
        fun end(forget: () -> Unit) {
            session++
            forget()
        }
    }

    private val gate = Gate()
    private const val PREFERENCES = "live_titles"
    private val ID = Regex("[A-Za-z0-9_=-]+")
    private val json = Json { ignoreUnknownKeys = true }

    /** The title's address on this build's server, for an id shaped like the server's; never another path. */
    fun url(id: String): String? = if (ID.matches(id)) "${AppConfig.baseUrl}/native/live_titles/$id" else null

    fun parse(body: String): Title? = runCatching { json.decodeFromString<Title>(body) }.getOrNull()

    /** Whether to ask the server again: nothing kept, another run, or a chat still waiting for its title. */
    fun needsFetch(kept: Kept?, run: String?): Boolean = kept == null || kept.run != run || kept.title.titled == false

    /** The title kept for the chat whose thread is [thread], if any. */
    fun kept(context: Context, thread: String): Kept? = preferences(context).getString(thread, null)
        ?.let { runCatching { json.decodeFromString<Kept>(it) }.getOrNull() }

    /**
     * Asks the server for [progress]'s chat title when it's due, and keeps the
     * answer. Blocks, at most [TIMEOUT_MS] a try: call it off the main thread,
     * before the Live Update is drawn.
     */
    fun refresh(context: Context, progress: LiveProgress) {
        val id = progress.titleRef ?: return
        if (!needsFetch(kept(context, progress.thread), progress.run)) return
        val key = { PushNotifications.notificationKey(context) }
        val ask = gate.ask(key) ?: return
        val title = fetchWithin(id, ask.key) ?: return
        gate.keepIf(ask, key) { keep(context, progress.thread, progress.run, title) }
    }

    /** Keeps what the server said of [thread]'s chat for [run]. */
    fun keep(context: Context, thread: String, run: String?, title: Title) {
        preferences(context).edit { putString(thread, json.encodeToString(Kept(run, title))) }
    }

    /** Signing out forgets every chat's title, and any still being asked for. */
    fun forget(context: Context) {
        gate.end { preferences(context).edit(commit = true) { clear() } }
    }

    /**
     * [fetch] with one deadline for the whole lookup, since the step it heads
     * waits for it: a slow answer is dropped after [TIMEOUT_MS], and the card
     * shows the step's words.
     */
    private fun fetchWithin(id: String, key: String): Title? {
        val lookup = FutureTask { fetch(id, key) }
        thread(isDaemon = true, name = "live-title") { lookup.run() }
        return try {
            lookup.get(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            lookup.cancel(true)
            null
        } catch (_: ExecutionException) {
            null
        } catch (_: InterruptedException) {
            null
        }
    }

    /** The title [id] names, or null. Blocks: call it off the main thread. */
    fun fetch(id: String, key: String): Title? {
        val url = url(id) ?: return null
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (_: IOException) {
                return null
            }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parse(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
