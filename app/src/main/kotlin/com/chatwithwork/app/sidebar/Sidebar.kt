package com.chatwithwork.app.sidebar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the sidebar shows, as the server describes it at
 * `GET /:account/native/sidebar` (docs/server-contract.md, "The sidebar").
 * Every URL in it is a path on this server. Unknown keys are ignored and
 * missing ones take their defaults, so the server can add to it freely.
 */
@Serializable
data class Sidebar(
    val organization: Organization,
    val organizations: List<Organization> = emptyList(),
    val person: Person? = null,
    val list: ChatList? = null,
    val lists: List<ChatList> = emptyList(),
    val links: Links = Links(),
    val waiting: Waiting = Waiting(),
    val bots: Bots? = null,
    val pins: List<Chat> = emptyList(),
    /** The person's own sections, in their order; [chats] are then the list's chats in none of them. */
    val sections: List<Section> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val next: String? = null
) {
    @Serializable
    data class Organization(
        val id: String,
        val name: String,
        val initials: String = "",
        val current: Boolean = false,
        val role: String? = null,
        val url: String? = null
    )

    @Serializable
    data class Person(
        val name: String = "",
        val email: String? = null,
        val initials: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null
    )

    @Serializable
    data class ChatList(
        val key: String,
        val name: String,
        /** `recent`, `all`, or `project`. */
        val kind: String = "recent",
        val hq: Boolean = false,
        val pinned: Boolean = false,
        val current: Boolean = false
    ) {
        val isProject: Boolean get() = kind == "project"
    }

    @Serializable
    data class Links(
        @SerialName("new_chat") val newChat: String? = null,
        val bots: String? = null,
        val settings: String? = null,
        val projects: String? = null,
        @SerialName("new_project") val newProject: String? = null,
        val organizations: String? = null,
        @SerialName("new_organization") val newOrganization: String? = null,
        val decisions: String? = null
    )

    @Serializable
    data class Waiting(
        val count: Int = 0,
        val items: List<WaitingItem> = emptyList(),
        /** The Decisions page (servers from 2026-10-26); without it the row lists [items]. */
        val url: String? = null
    )

    @Serializable
    data class WaitingItem(
        val title: String,
        val url: String,
        /** `approval`, `question`, or `bot`. */
        val kind: String = "approval",
        val project: String? = null
    )

    @Serializable
    data class Bots(
        val count: Int = 0,
        val attention: Boolean = false,
        @SerialName("next_run") val nextRun: String? = null,
        /** The Bots page. */
        val url: String? = null,
        /** New bot: the recipes, Describe your bot, Start from nothing. */
        @SerialName("new_url") val newUrl: String? = null,
        /**
         * The person's bots and the shared ones they read, as the web's Bots
         * view lists them: those that need them first. Null from servers
         * before the apps' Chats | Bots switch, which keep the Bots row.
         */
        val items: List<Bot>? = null
    )

    /** One bot in the sidebar's Bots view (the web's `bots/_sidebar_bot`). */
    @Serializable
    data class Bot(
        val id: Long,
        val name: String,
        val url: String,
        /** `needs_you` or `quiet`, the web's two headings; the items come in that order. */
        val group: String = QUIET,
        /** The project a shared bot is in; null for the person's own. */
        val project: String? = null,
        /** `waiting`, `failed`, `working`, `paused`, `next`, `listening`, or `idle`. */
        val state: String = "idle",
        /** The state in the web's words ("Waiting for you", "Next 08:30"). */
        @SerialName("state_label") val stateLabel: String? = null,
        /** `attention`, `negative`, `live`, `positive`, or none. */
        val tone: String? = null,
        /** How many decisions wait for the person in it. */
        val waiting: Int = 0,
        /** One of its chats is answering now. */
        val working: Boolean = false,
        @SerialName("next_run") val nextRun: String? = null
    ) {
        val needsYou: Boolean get() = group == NEEDS_YOU

        companion object {
            const val NEEDS_YOU = "needs_you"
            const val QUIET = "quiet"
        }
    }

    @Serializable
    data class Chat(
        val number: Long,
        val title: String,
        val url: String,
        /** `idle`, `processing`, or `error`. */
        val state: String = "idle",
        val waiting: Boolean = false,
        val project: String? = null,
        @SerialName("asked_at") val askedAt: String? = null,
        val group: String? = null,
        /** What a long press offers, as the web row's menu does. */
        val actions: List<Action> = emptyList()
    ) {
        val isProcessing: Boolean get() = state == "processing"
    }

    /**
     * One of the person's own sidebar sections (the server's `ChatSection`):
     * a heading of theirs holding this list's chats they put in it, newest
     * question first, open or closed as they left it on any device.
     */
    @Serializable
    data class Section(
        val id: Long,
        val name: String,
        val collapsed: Boolean = false,
        val chats: List<Chat> = emptyList(),
        /** Its Rename page. */
        @SerialName("edit_url") val editUrl: String? = null,
        /** POST closes it, DELETE opens it. */
        @SerialName("collapse_url") val collapseUrl: String? = null,
        /** PATCH with `position` moves it. */
        @SerialName("position_url") val positionUrl: String? = null
    ) {
        /**
         * Opening or closing it on the server, sent like a chat's action by
         * the page showing, or null from a server without the URL.
         */
        val toggleAction: Action?
            get() = collapseUrl?.let { url ->
                Action(key = "collapse", title = name, method = if (collapsed) "delete" else "post", url = url)
            }
    }

    /**
     * One of a chat's actions: a `get` opens [url]; any other method goes to
     * the page showing to send (SidebarComponent), behind [confirm].
     */
    @Serializable
    data class Action(
        val key: String,
        val title: String,
        val method: String = "get",
        val url: String,
        val androidImage: String? = null,
        val confirm: String? = null,
        val destructive: Boolean = false
    ) {
        val opensPage: Boolean get() = method == "get"
    }

    /** This page and the next one together, for loading more as the list scrolls. */
    fun plus(next: Sidebar): Sidebar = copy(
        chats =
            chats + next.chats.filter { new -> chats.none { it.url == new.url } },
        next = next.next
    )

    /** Whether anything in it (a chat, or a bot's) is answering right now, to keep it fresh while the sidebar is open. */
    val anyProcessing: Boolean
        get() = (chats + pins + sections.flatMap { it.chats }).any { it.isProcessing } ||
            bots?.items.orEmpty().any { it.working }

    /** This sidebar with section [id] shown open or closed, before the server hears of it. */
    fun withCollapsed(id: Long, collapsed: Boolean): Sidebar = copy(
        sections = sections.map { if (it.id == id) it.copy(collapsed = collapsed) else it }
    )

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }

        fun parse(text: String): Sidebar = json.decodeFromString(serializer(), text)
    }
}
