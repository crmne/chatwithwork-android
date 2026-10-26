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
        @SerialName("next_run") val nextRun: String? = null
    )

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

    /** Whether anything in it is answering right now, to keep it fresh while the sidebar is open. */
    val anyProcessing: Boolean get() = (chats + pins).any { it.isProcessing }

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
