package com.chatwithwork.app.routing

import com.chatwithwork.app.AppConfig
import java.net.URI

/**
 * The URLs the app knows about. Pages inside an organization carry its id as
 * the first path segment (/482139075/chats/42); the unprefixed paths (/chats)
 * redirect into the organization the person used last, so the tabs start
 * there and never need to know the id.
 */
object AppUrls {
    private val base = URI(AppConfig.baseUrl)

    val chats = "${AppConfig.baseUrl}/chats"
    val projects = "${AppConfig.baseUrl}/projects"
    val settings = "${AppConfig.baseUrl}/settings"
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

    /** The new-chat page, inside [account] when it's known. */
    fun newChat(account: String?): String = page("/chats/new", account)

    // Rails' AccountSlug::PATTERN: seven digits or more.
    private val accountPrefix = Regex("^/(\\d{7,})(?=/|$)")

    /** Which part of the app a path belongs to, for picking its tab. */
    enum class Section { CHATS, PROJECTS, SETTINGS, OTHER }

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

    fun section(url: String): Section {
        val path = pathWithinAccount(url)
        return when {
            path == "/chats" || path.startsWith("/chats/") -> Section.CHATS
            path == "/projects" || path.startsWith("/projects/") -> Section.PROJECTS
            path == "/settings" || path.startsWith("/settings/") -> Section.SETTINGS
            else -> Section.OTHER
        }
    }

    /** Whether [url] is a tab's own first page (the chat list, the project list, settings). */
    fun isTabRoot(url: String): Boolean = when (pathWithinAccount(url)) {
        "/chats", "/projects", "/settings" -> true
        else -> false
    }

    fun isNewChat(url: String): Boolean = pathWithinAccount(url) == "/chats/new"

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

    /** Turbo's recede_, resume_ and refresh_historical_location. */
    fun isHistoricalLocation(url: String): Boolean = when (parse(url)?.rawPath) {
        "/recede_historical_location", "/resume_historical_location", "/refresh_historical_location" -> true
        else -> false
    }

    /** `recede_or_redirect_to`: go back a screen. */
    fun isRecede(url: String): Boolean = parse(url)?.rawPath == "/recede_historical_location"

    /** `resume_or_redirect_to`: stay where the person was. */
    val resume = "${AppConfig.baseUrl}/resume_historical_location"

    /** Uploaded files and previews (Active Storage): not pages, so not for the web view. */
    fun isFile(url: String): Boolean = parse(url)?.rawPath.orEmpty().startsWith("/rails/active_storage/")

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf { it.host != null }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
