package com.chatwithwork.app.sidebar

/**
 * The sidebar's rows, top to bottom, from the server's [Sidebar] (or nothing
 * yet: an older server, offline, the first launch) and what the person typed
 * in its search. Kept apart from the views so it can be tested.
 */
sealed interface SidebarRow {
    data class Switcher(val list: Sidebar.ChatList) : SidebarRow

    data object Search : SidebarRow

    data class Nav(val kind: Kind, val meta: String? = null, val attention: Boolean = false) : SidebarRow {
        enum class Kind { NEW_CHAT, WAITING, BOTS }
    }

    data class Section(val title: String) : SidebarRow

    /** The heading of one of the person's own sections, which opens and closes it. */
    data class ChatSection(val section: Sidebar.Section) : SidebarRow

    data class ChatRow(val chat: Sidebar.Chat, val selected: Boolean) : SidebarRow

    data class Empty(val kind: Kind) : SidebarRow {
        enum class Kind { NO_CHATS, NO_MATCHES }
    }

    companion object {
        /**
         * [query] filters the chats and pins by title, every word anywhere,
         * ignoring case, as the web's sidebar search does. [current] is the
         * page showing, whose chat is marked. [pinnedTitle] heads the pins.
         * Without a [sidebar], the rows that need no data: New chat and Bots
         * (when [botsKnown]). [otherTitle] heads the list's chats in none of
         * the person's sections, when they have any.
         */
        fun build(
            sidebar: Sidebar?,
            query: String,
            current: String?,
            pinnedTitle: String,
            botsKnown: Boolean,
            otherTitle: String = "Other chats"
        ): List<SidebarRow> {
            if (sidebar == null) {
                return listOfNotNull(Nav(Nav.Kind.NEW_CHAT), Nav(Nav.Kind.BOTS).takeIf { botsKnown })
            }

            val rows = mutableListOf<SidebarRow>()
            sidebar.list?.let { rows += Switcher(it) }
            rows += Search
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

            if (words.isEmpty()) {
                rows += Nav(Nav.Kind.NEW_CHAT)
                rows +=
                    Nav(
                        Nav.Kind.WAITING,
                        meta = sidebar.waiting.count.takeIf { it > 0 }?.toString(),
                        attention =
                            sidebar.waiting.count > 0
                    )
                sidebar.bots?.let { bots ->
                    val meta = bots.count.takeIf { it > 0 }?.toString() ?: bots.nextRun
                    rows += Nav(Nav.Kind.BOTS, meta = meta, attention = bots.attention || bots.count > 0)
                }
            }

            fun matches(chat: Sidebar.Chat): Boolean = words.all { chat.title.lowercase().contains(it) }

            val pins = sidebar.pins.filter(::matches)
            if (pins.isNotEmpty()) {
                rows += Section(pinnedTitle)
                pins.forEach { rows += ChatRow(it, selected = isCurrent(it, current)) }
            }

            // The person's sections, as the web's: each heading, then its
            // chats unless it's closed. A search lists every match in them.
            var sectioned = 0
            sidebar.sections.forEach { section ->
                if (words.isEmpty()) {
                    rows += ChatSection(section)
                    sectioned += section.chats.size
                    if (!section.collapsed) {
                        section.chats.forEach { rows += ChatRow(it, selected = isCurrent(it, current)) }
                    }
                } else {
                    section.chats.filter(::matches).forEach {
                        sectioned++
                        rows += ChatRow(it, selected = isCurrent(it, current))
                    }
                }
            }

            val chats = sidebar.chats.filter(::matches)
            if (sidebar.sections.isNotEmpty() && words.isEmpty()) {
                // Below the sections, the rest under one heading, as on the web.
                if (chats.isNotEmpty()) rows += Section(otherTitle)
                chats.forEach { rows += ChatRow(it, selected = isCurrent(it, current)) }
            } else {
                var group: String? = null
                chats.forEach { chat ->
                    if (chat.group != null && chat.group != group) {
                        group = chat.group
                        rows += Section(chat.group)
                    }
                    rows += ChatRow(chat, selected = isCurrent(chat, current))
                }
            }

            if (chats.isEmpty() && pins.isEmpty() && sectioned == 0) {
                rows += Empty(if (words.isEmpty()) Empty.Kind.NO_CHATS else Empty.Kind.NO_MATCHES)
            }
            return rows
        }

        /** Whether [current] (a full URL, maybe with a query or anchor) is [chat]'s page. */
        fun isCurrent(chat: Sidebar.Chat, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            return path == chat.url.substringBefore('?').substringBefore('#')
        }
    }
}
