package com.chatwithwork.app.routing

import com.chatwithwork.app.AppConfig
import java.net.URI
import java.net.URLDecoder

/**
 * The URLs the app knows about. Pages inside an organization carry its id as
 * the first path segment (/482139075/chats/42); the unprefixed paths (/chats)
 * redirect into the organization the person used last, so the tabs start
 * there and never need to know the id.
 */
object AppUrls {
    private val base = URI(AppConfig.baseUrl)

    val signIn = "${AppConfig.baseUrl}/users/sign_in"
    val signUp = "${AppConfig.baseUrl}/users/sign_up"

    /**
     * The scheme the server ends browser sign-ins with
     * (chatwithwork://sign-in?..., chatwithwork://handoff?...), the same on
     * iOS. See docs/server-contract.md.
     */
    const val CALLBACK_SCHEME = "chatwithwork"

    /** A page of the app ([path] like "/chats"), inside [account] when it's known. */
    fun page(path: String, account: String?): String =
        if (account == null) "${AppConfig.baseUrl}$path" else "${AppConfig.baseUrl}/$account$path"

    /** The new-chat page, inside [account] when it's known, in [project] when one is given. */
    fun newChat(account: String?, project: String? = null): String =
        page("/chats/new", account) + (project?.let { "?project_id=$it" } ?: "")

    /** New bot, inside [account] when it's known. */
    fun newBot(account: String?): String = page("/bots/new", account)

    /**
     * Where the app opens: the organization's home, which the server sends on
     * to Decisions, whose "All clear" carries New chat's composer when nothing
     * waits (docs/server-contract.md, "Places").
     */
    fun home(account: String?): String = page("/home", account)

    /** The organization's chat list, a place: where the app opens on a server without the home. */
    fun chats(account: String?): String = page("/chats", account)

    /** The organization's home ("/482139075/home") or the unprefixed one ("/home"), on this server. */
    fun isStartHome(url: String): Boolean = isAppUrl(url) && pathWithinAccount(url) == "/home"

    /**
     * Whether [url] answering [status] means the server has no home yet (one
     * from before 2026-11-17, a self-hosted release among them), so the app
     * opens the chat list instead, as builds that showed New chat as a
     * modal did.
     */
    fun homeMissing(url: String, status: Int?): Boolean = status == HTTP_NOT_FOUND && isStartHome(url)

    /** The Decisions page, inside [account] when it's known. */
    fun decisions(account: String?): String = page("/decisions", account)

    // Rails' AccountSlug::PATTERN: seven digits or more.
    private val accountPrefix = Regex("^/(\\d{7,})(?=/|$)")

    /** Whether [url] is a page of this app (same scheme, host, and port). */
    fun isAppUrl(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.equals(base.scheme, ignoreCase = true) &&
            uri.host.equals(base.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(base)
    }

    /**
     * Whether this build's server is on a private network (10.0.2.2, the
     * emulator's view of the computer, or a computer on the LAN), which
     * Android 17 only lets an app reach with the local network permission.
     */
    fun serverIsOnPrivateNetwork(): Boolean {
        val octets = base.host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    /** Whether [url] is the end of a browser sign-in (chatwithwork://...). */
    fun isAuthCallback(url: String): Boolean =
        runCatching { URI(url).scheme }.getOrNull().equals(CALLBACK_SCHEME, ignoreCase = true)

    /** The organization id in [url]'s path, if it has one. */
    fun account(url: String): String? {
        val path = parse(url)?.rawPath ?: return null
        return accountPrefix.find(path)?.groupValues?.get(1)
    }

    /** [url]'s path without the organization prefix ("/482139075/chats/42" is "/chats/42"). */
    fun pathWithinAccount(url: String): String {
        val path = parse(url)?.rawPath.orEmpty().ifEmpty { "/" }
        return accountPrefix.replace(path, "").ifEmpty { "/" }
    }

    /** A path the server gave ("/482139075/chats/42") as a URL on this build's server. */
    fun absolute(path: String): String =
        if (path.startsWith("/") && !path.startsWith("//")) "${AppConfig.baseUrl}$path" else path

    /**
     * The sidebar's JSON for [account], showing [list] (a key the server
     * gave: "recent", "all", or a project's number), with every chat when
     * [all] (for searching). See docs/server-contract.md, "The sidebar".
     */
    fun sidebar(account: String, list: String? = null, all: Boolean = false): String {
        val query =
            listOfNotNull(
                list?.let { "list=" + java.net.URLEncoder.encode(it, "UTF-8") },
                "all=1".takeIf { all }
            )
        return "${AppConfig.baseUrl}/$account/native/sidebar" +
            if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    /** The chat's number in a chat's page ("/482139075/chats/42" is 42). */
    fun chatNumber(url: String): Long? =
        Regex("^/chats/(\\d+)$").find(pathWithinAccount(url))?.groupValues?.get(1)?.toLongOrNull()

    /** A chat's page, or the new chat page: the pages with a composer that run under the top bar. */
    fun isChat(url: String): Boolean = isNewChat(url) || isOpenChat(url)

    /** A chat's page, a place the navigator can start on. */
    fun isOpenChat(url: String): Boolean = Regex("^/chats/\\d+$").matches(pathWithinAccount(url))

    fun isNewChat(url: String): Boolean = pathWithinAccount(url) == "/chats/new"

    /** New bot's sentence page, which turns into the bot's first chat as New chat does. */
    fun isNewBot(url: String): Boolean = pathWithinAccount(url) == "/bots/new"

    /**
     * New chat or New bot: pages (path configuration v4) that the sidebar and
     * the app's ways in from outside (a shortcut, the assistant, a share) open
     * as a place, the root, as the sidebar opens every row.
     */
    fun opensAsPlace(url: String): Boolean = isAppUrl(url) && (isNewChat(url) || isNewBot(url))

    /**
     * The pages whose composer turns into the chat it starts, in place, as the
     * question goes (the server's `chats/_sending`): New chat, New bot's
     * sentence, the home and Decisions (whose "All clear" carries New chat's
     * composer).
     */
    fun turnsIntoChat(url: String): Boolean =
        isNewChat(url) || isNewBot(url) || isStartHome(url) || pathWithinAccount(url) == "/decisions"

    /** A chat's page as a place, not one a page opened about something on it (`?from=`). */
    fun isChatPlace(url: String): Boolean =
        isOpenChat(url) && parse(url)?.rawQuery.orEmpty().split('&').none { it.startsWith("from=") }

    /**
     * Sign-in, sign-up, password, and confirmation pages: shown in the sign-in
     * modal, which is done once it leaves them.
     */
    fun isAuthentication(url: String): Boolean {
        val segments =
            parse(url)
                ?.rawPath
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        return when {
            segments == listOf("users") -> true

            // a sign-up form re-rendered after a failed POST
            segments.firstOrNull() == "users" -> segments.getOrNull(1) in AUTHENTICATION

            segments.firstOrNull() == "native" -> true

            else -> false
        }
    }

    private val AUTHENTICATION = setOf("sign_in", "sign_up", "password", "confirmation", "unlock", "auth")

    /** The site's home page, a marketing page outside the app. */
    fun isHome(url: String): Boolean = parse(url)?.rawPath.orEmpty().ifEmpty { "/" } == "/"

    /** The sign-in form itself, where Devise sends a request without a session. */
    fun isSignInForm(url: String): Boolean = parse(url)?.rawPath == "/users/sign_in"

    /**
     * Turbo's recede_, resume_ and refresh_historical_location, also under an
     * organization's prefix ("/482139075/recede_historical_location"), where
     * the server's account-scoped controllers send them.
     */
    fun isHistoricalLocation(url: String): Boolean = isAppUrl(url) && pathWithinAccount(url) in HISTORICAL

    /** `recede_or_redirect_to`: go back a screen. */
    fun isRecede(url: String): Boolean = isAppUrl(url) && pathWithinAccount(url) == "/recede_historical_location"

    /** What a historical location says to show: its `notice`, or its `alert`, which stays up longer. */
    data class Notice(val message: String, val long: Boolean)

    /** [url]'s notice or alert (`recede_or_redirect_to url, notice: "Chat renamed."`), if it's a historical location with one. */
    fun historicalNotice(url: String): Notice? {
        if (!isHistoricalLocation(url)) return null
        val query = parse(url)?.rawQuery ?: return null
        val values =
            query.split('&').mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                val value = parts.getOrNull(1)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                value?.takeIf { it.isNotBlank() }?.let { parts[0] to it }
            }.toMap()
        values["notice"]?.let { return Notice(it, long = false) }
        values["alert"]?.let { return Notice(it, long = true) }
        return null
    }

    /** `resume_or_redirect_to`: stay where the person was. */
    val resume = "${AppConfig.baseUrl}/resume_historical_location"

    /** Uploaded files and previews (Active Storage): not pages, so not for the web view. */
    fun isFile(url: String): Boolean = parse(url)?.rawPath.orEmpty().startsWith("/rails/active_storage/")

    /** A chat's file in its viewer (the server's chats/files/show), which zooms like a document. */
    fun isFileViewer(url: String): Boolean = Regex("^/chats/\\d+/files/").containsMatchIn(pathWithinAccount(url))

    private const val HTTP_NOT_FOUND = 404

    private val HISTORICAL =
        setOf("/recede_historical_location", "/resume_historical_location", "/refresh_historical_location")

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf { it.host != null }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
