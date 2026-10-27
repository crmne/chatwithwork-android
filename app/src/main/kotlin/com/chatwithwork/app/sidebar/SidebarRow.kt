package com.chatwithwork.app.sidebar

/**
 * The sidebar's rows, top to bottom, from the server's [Sidebar] (or nothing
 * yet: an older server, offline, the first launch) and what the person typed
 * in its search. Kept apart from the views so it can be tested.
 */
sealed interface SidebarRow {
    data class Switcher(val list: Sidebar.ChatList) : SidebarRow

    data object Search : SidebarRow

    data class Nav(
        val kind: Kind,
        val meta: String? = null,
        val attention: Boolean = false,
        /** Where it leads, for those the server names (New bot). */
        val url: String? = null
    ) : SidebarRow {
        enum class Kind { NEW_CHAT, WAITING, BOTS, NEW_BOT }
    }

    /** The Chats | Bots switch under Decisions; Bots carries a dot when [botsAttention], never a number. */
    data class Views(val view: View, val botsAttention: Boolean) : SidebarRow

    /** What the sidebar shows under its switch. */
    enum class View {
        CHATS,
        BOTS;

        companion object {
            fun from(key: String?): View = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: CHATS
        }
    }

    /**
     * The Bots view's card, in the list switcher's place: "Bots in [organization]",
     * how many, what's working and waiting, opening the Bots page ([url]).
     */
    data class BotsCard(
        val organization: String,
        val count: Int,
        val working: Int,
        val waiting: Int,
        val url: String?
    ) : SidebarRow

    data class BotRow(val bot: Sidebar.Bot, val selected: Boolean) : SidebarRow

    /** No bots yet: a line, and "Start from a recipe" ([url], New bot). */
    data class NoBots(val url: String?) : SidebarRow

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
         *
         * A server that lists the person's bots (`bots.items`) gets the
         * Chats | Bots switch under Decisions instead of the Bots row, and
         * [view] picks the half: Bots draws, in the same places, a card for
         * the list switcher, New bot for New chat, and the bots for the
         * chats, under [needsYouTitle] and [quietTitle] when there are both.
         */
        fun build(
            sidebar: Sidebar?,
            query: String,
            current: String?,
            pinnedTitle: String,
            botsKnown: Boolean,
            otherTitle: String = "Other chats",
            view: View = View.CHATS,
            needsYouTitle: String = "Needs you",
            quietTitle: String = "Quiet"
        ): List<SidebarRow> {
            if (sidebar == null) {
                return listOfNotNull(Nav(Nav.Kind.NEW_CHAT), Nav(Nav.Kind.BOTS).takeIf { botsKnown })
            }

            val bots = sidebar.bots
            val botItems = bots?.items
            if (bots != null && botItems != null && view == View.BOTS) {
                return buildBots(sidebar, bots, botItems, current, needsYouTitle, quietTitle)
            }

            val rows = mutableListOf<SidebarRow>()
            sidebar.list?.let { rows += Switcher(it) }
            rows += Search
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

            if (words.isEmpty()) {
                rows += Nav(Nav.Kind.NEW_CHAT)
                rows += decisions(sidebar)
                if (bots != null && botItems != null) {
                    rows += Views(View.CHATS, botsAttention = bots.attention)
                } else if (bots != null) {
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

        private fun decisions(sidebar: Sidebar) = Nav(
            Nav.Kind.WAITING,
            meta = sidebar.waiting.count.takeIf { it > 0 }?.toString(),
            attention = sidebar.waiting.count > 0
        )

        private fun buildBots(
            sidebar: Sidebar,
            bots: Sidebar.Bots,
            items: List<Sidebar.Bot>,
            current: String?,
            needsYouTitle: String,
            quietTitle: String
        ): List<SidebarRow> {
            val rows = mutableListOf<SidebarRow>()
            rows +=
                BotsCard(
                    organization = sidebar.organization.name,
                    count = items.size,
                    working = items.count { it.working },
                    waiting = items.sumOf { it.waiting },
                    url = bots.url ?: sidebar.links.bots
                )
            rows += Nav(Nav.Kind.NEW_BOT, url = bots.newUrl)
            rows += decisions(sidebar)
            rows += Views(View.BOTS, botsAttention = bots.attention)

            if (items.isEmpty()) {
                rows += NoBots(bots.newUrl)
                return rows
            }
            val (needs, quiet) = items.partition { it.needsYou }
            listOf(needsYouTitle to needs, quietTitle to quiet).forEach { (title, group) ->
                if (group.isEmpty()) return@forEach
                if (needs.isNotEmpty() && quiet.isNotEmpty()) rows += Section(title)
                group.forEach { rows += BotRow(it, selected = isCurrent(it, current)) }
            }
            return rows
        }

        /** Whether [current] is [bot]'s page or one under it (its chats, its settings), as the web marks it. */
        fun isCurrent(bot: Sidebar.Bot, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            val url = bot.url.substringBefore('?').substringBefore('#')
            return path == url || path.startsWith("$url/")
        }

        /** Whether [current] (a full URL, maybe with a query or anchor) is [chat]'s page. */
        fun isCurrent(chat: Sidebar.Chat, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            return path == chat.url.substringBefore('?').substringBefore('#')
        }
    }
}
