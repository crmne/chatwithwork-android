package com.chatwithwork.app.sidebar

import com.chatwithwork.app.routing.AppUrls
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The server's sidebar page (`GET /:account/sidebar`), which the app keeps in
 * a web view of its own in the drawer (SidebarWebView): the rules it follows,
 * kept apart from Android so they're tested on their own. See
 * docs/server-contract.md, "The sidebar page".
 */
object SidebarPage {
    /** The name the page finds the app's message handler under. */
    const val INTERFACE = "ChatWithWorkSidebar"

    /**
     * The page's web view says it's the app (so the server draws it for a
     * phone), but lists no bridge components: none of the page's bridge
     * controllers may load there, where no bridge answers them.
     */
    fun userAgent(prefix: String, webViewUserAgent: String): String = "$prefix $webViewUserAgent"

    /** What the page posts (native_sidebar_controller.js). */
    sealed interface Message {
        /** Drawn and listening: the app answers with the page on its screen. */
        data object Ready : Message

        /** A link or a form's answer that leads elsewhere: the app opens [url] in its navigator. */
        data class Visit(val url: String) : Message

        /** The header's close button. */
        data object Close : Message

        /** Ask before a request, in the app's alert; the app answers [id] with [Script.answer]. */
        data class Confirm(
            val id: String,
            val title: String,
            val description: String?,
            val confirm: String,
            val dismiss: String,
            val destructive: Boolean
        ) : Message
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** [raw] as one of the page's messages; null for anything else. */
    fun parse(raw: String): Message? {
        val data = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return when (data.string("event")) {
            "ready" -> Message.Ready

            "visit" -> data.string("url")?.let(Message::Visit)

            "close" -> Message.Close

            "confirm" -> {
                val id = data.string("id") ?: return null
                Message.Confirm(
                    id = id,
                    title = data.string("title").orEmpty(),
                    description = data.string("description")?.takeIf { it.isNotBlank() },
                    confirm = data.string("confirm") ?: "OK",
                    dismiss = data.string("dismiss") ?: "Cancel",
                    destructive = (data["destructive"] as? JsonPrimitive)?.booleanOrNull == true
                )
            }

            else -> null
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** What happens to a load the page's web view is asked to make. */
    enum class Load {
        /** The page itself (drawn again): it loads there. */
        ALLOW,

        /** A page of this server: the app's navigator opens it. */
        VISIT,

        /** Another site, or another app's link: the browser opens it. */
        EXTERNAL
    }

    /** Where [url] goes, the page being at [pagePath]. Nothing but the page ever loads in the drawer. */
    fun load(url: String, pagePath: String?): Load {
        if (!AppUrls.isAppUrl(url)) return Load.EXTERNAL
        val path = runCatching { URI(url).rawPath }.getOrNull()
        return if (pagePath != null && path == pagePath) Load.ALLOW else Load.VISIT
    }

    /** The `sidebar` component's `connect`: the page to show, and the path the page on screen belongs under. */
    @Serializable
    data class Connect(val page: String? = null, val belongsTo: String? = null)

    /**
     * The page to show: what the latest `connect` named, once one came, even
     * none (an older server: the native sidebar); before any, the one this
     * organization named last time, so it loads before the first page does.
     */
    fun pageFor(connect: Connect?, remembered: String?): String? =
        if (connect != null) connect.page?.takeIf(::isPath) else remembered?.takeIf(::isPath)

    private fun isPath(path: String) = path.startsWith("/") && !path.startsWith("//")

    /** The scripts the app runs in the page, on `window.chatWithWorkSidebar`. */
    object Script {
        fun current(url: String, belongsTo: String?): String =
            "window.chatWithWorkSidebar && window.chatWithWorkSidebar.current(${quote(
                url
            )}, ${belongsTo?.let(::quote) ?: "null"})"

        fun answer(id: String, confirmed: Boolean): String =
            "window.chatWithWorkSidebar && window.chatWithWorkSidebar.answer(${quote(id)}, $confirmed)"

        const val REFRESH = "window.chatWithWorkSidebar && window.chatWithWorkSidebar.refresh()"

        private fun quote(value: String) = JsonPrimitive(value).toString()
    }
}
