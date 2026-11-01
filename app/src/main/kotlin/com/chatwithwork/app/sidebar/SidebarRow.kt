package com.chatwithwork.app.sidebar

/**
 * The sidebar's rows, top to bottom, from the server's [Sidebar] (or nothing
 * yet: an older server, offline, the first launch) and what the person typed
 * in its search. Kept apart from the views so it can be tested.
 */
sealed interface SidebarRow {
    /**
     * A view's head, one row for Chats and Bots as on the web (`.sidebar__head`):
     * what the [list] is, and the square plus beside it, starting a chat or
     * opening [newUrl], which guests don't get ([canAdd]). [adding] while
     * that page shows; [waiting] says how many decisions wait in the Bots
     * view's list.
     */
    data class Head(
        val list: Sidebar.ChatList,
        val view: View,
        val canAdd: Boolean,
        val newUrl: String? = null,
        val adding: Boolean = false,
        val waiting: Int = 0
    ) : SidebarRow

    /** The search under the head, in both views: its hint follows the list. */
    data class Search(val view: View = View.CHATS, val project: String? = null) : SidebarRow

    /** The list's last row once it holds everything: its page ([project] names a project's). */
    data class SeeAll(val url: String, val project: String?) : SidebarRow

    data class Nav(
        val kind: Kind,
        val meta: String? = null,
        val attention: Boolean = false,
        /** Where it leads, for those the server names (New bot). */
        val url: String? = null
    ) : SidebarRow {
        enum class Kind { NEW_CHAT, BOTS, NEW_BOT }
    }

    /**
     * Decisions, the sidebar's first control, in both views and for guests:
     * how many wait ([count], `waiting.count`), opening the Decisions page.
     * [selected] while that page shows.
     */
    data class Decisions(val count: Int, val selected: Boolean) : SidebarRow

    /** The Chats | Bots switch, just under Decisions; Bots carries a dot when [botsAttention], never a number. */
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
     * What an older server's Bots view has for a head: "Bots in [organization]",
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

    /** No bots at all: a line, and New bot ([url]). */
    data class NoBots(val url: String?) : SidebarRow

    data class Section(val title: String) : SidebarRow

    /** The heading of one of the person's own sections, which opens and closes it. */
    data class ChatSection(val section: Sidebar.Section) : SidebarRow

    data class ChatRow(val chat: Sidebar.Chat, val selected: Boolean, val pinned: Boolean = false) : SidebarRow

    /** A line where a list has nothing; [project] names the project in NO_PROJECT_CHATS. */
    data class Empty(val kind: Kind, val project: String? = null) : SidebarRow {
        enum class Kind { NO_CHATS, NO_LISTED_CHATS, NO_PROJECT_CHATS, NO_MATCHES, NO_BOTS, NO_BOT_MATCHES }
    }

    companion object {
        /**
         * [query] filters the chats and pins by title, every word anywhere,
         * ignoring case, as the web's sidebar search does. [current] is the
         * page showing, whose chat is marked. [pinnedTitle] heads the pins.
         * Without a [sidebar], the rows that need no data: New chat and Bots
         * (when [botsKnown]). With one, Decisions leads, in both views and
         * while searching, as on the web. [otherTitle] heads the list's chats
         * in none of the person's sections, when they have any.
         *
         * A server that lists the person's bots (`bots.items`) gets the
         * Chats | Bots switch under Decisions instead of the Bots row, and
         * [view] picks the half. Both open the same way, so switching moves
         * nothing: the [Head], then the [Search]. Bots then lists the bots
         * of [botList] (`bots.lists`), under [needsYouTitle] and [quietTitle]
         * when there are both.
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
            quietTitle: String = "Quiet",
            botList: String? = null
        ): List<SidebarRow> {
            if (sidebar == null) {
                return listOfNotNull(Nav(Nav.Kind.NEW_CHAT), Nav(Nav.Kind.BOTS).takeIf { botsKnown })
            }

            val bots = sidebar.bots
            val botItems = bots?.items
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (bots != null && botItems != null && view == View.BOTS) {
                val list = bots.chosenList(botList)
                return if (bots.lists.isEmpty() || list == null) {
                    buildBotsCard(sidebar, bots, botItems, current, needsYouTitle, quietTitle)
                } else {
                    buildBots(sidebar, bots, botItems, list, words, current, needsYouTitle, quietTitle)
                }
            }

            val rows = mutableListOf<SidebarRow>(decisions(sidebar, current))
            if (bots != null && botItems != null) rows += Views(View.CHATS, botsAttention = bots.attention)
            val list = sidebar.list
            val newChat = sidebar.links.newChat
            if (list != null) {
                rows += Head(
                    list,
                    View.CHATS,
                    canAdd = newChat != null,
                    newUrl = newChat,
                    adding = isPage(newChat, current)
                )
            } else if (newChat != null) {
                rows += Nav(Nav.Kind.NEW_CHAT)
            }
            rows += Search(View.CHATS, list?.takeIf { it.isProject }?.name)

            if (words.isEmpty() && bots != null && botItems == null) {
                val meta = bots.count.takeIf { it > 0 }?.toString() ?: bots.nextRun
                rows += Nav(Nav.Kind.BOTS, meta = meta, attention = bots.attention || bots.count > 0)
            }

            fun matches(chat: Sidebar.Chat): Boolean = words.all { chat.title.lowercase().contains(it) }

            val pins = sidebar.pins.filter(::matches)
            if (pins.isNotEmpty()) {
                rows += Section(pinnedTitle)
                pins.forEach { rows += ChatRow(it, selected = isCurrent(it, current), pinned = true) }
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

            // One list by latest question, with no days, as on the web; below
            // the sections, under one heading.
            val chats = sidebar.chats.filter(::matches)
            if (sidebar.sections.isNotEmpty() && words.isEmpty() && chats.isNotEmpty()) rows += Section(otherTitle)
            chats.forEach { rows += ChatRow(it, selected = isCurrent(it, current)) }

            if (chats.isEmpty() && pins.isEmpty() && sectioned == 0) {
                rows += if (words.isEmpty()) emptyList(list) else Empty(Empty.Kind.NO_MATCHES)
            }
            if (words.isEmpty() && sidebar.next == null && chats.size + sectioned > 0) {
                sidebar.links.chats?.let { rows += SeeAll(it, list?.takeIf { it.isProject }?.name) }
            }
            return rows
        }

        private fun emptyList(list: Sidebar.ChatList?) = when {
            list == null || list.kind == "recent" -> Empty(Empty.Kind.NO_CHATS)
            list.isProject -> Empty(Empty.Kind.NO_PROJECT_CHATS, list.name)
            else -> Empty(Empty.Kind.NO_LISTED_CHATS)
        }

        private fun buildBots(
            sidebar: Sidebar,
            bots: Sidebar.Bots,
            items: List<Sidebar.Bot>,
            list: Sidebar.ChatList,
            words: List<String>,
            current: String?,
            needsYouTitle: String,
            quietTitle: String
        ): List<SidebarRow> {
            val rows = mutableListOf<SidebarRow>(decisions(sidebar, current))
            val listed = items.filter { list.key in it.lists }
            val newUrl = list.newUrl ?: bots.newUrl
            rows += Views(View.BOTS, botsAttention = bots.attention)
            rows += Head(
                list,
                View.BOTS,
                canAdd = newUrl != null,
                newUrl = newUrl,
                adding = isPage(newUrl, current),
                waiting = listed.sumOf { it.waiting }
            )
            rows += Search(View.BOTS, list.takeIf { it.isProject }?.name)

            val found = listed.filter { bot ->
                val text = "${bot.name} ${bot.description.orEmpty()}".lowercase()
                words.all { text.contains(it) }
            }
            when {
                items.isEmpty() -> rows += NoBots(newUrl)
                found.isEmpty() -> rows += Empty(if (words.isEmpty()) Empty.Kind.NO_BOTS else Empty.Kind.NO_BOT_MATCHES)
                else -> addBots(rows, found, current, needsYouTitle, quietTitle)
            }
            return rows
        }

        /** A server before `bots.lists`: the card and New bot for a head, and every bot, no search. */
        private fun buildBotsCard(
            sidebar: Sidebar,
            bots: Sidebar.Bots,
            items: List<Sidebar.Bot>,
            current: String?,
            needsYouTitle: String,
            quietTitle: String
        ): List<SidebarRow> {
            val rows = mutableListOf<SidebarRow>(decisions(sidebar, current))
            rows += Views(View.BOTS, botsAttention = bots.attention)
            rows +=
                BotsCard(
                    organization = sidebar.organization.name,
                    count = items.size,
                    working = items.count { it.working },
                    waiting = items.sumOf { it.waiting },
                    url = bots.url ?: sidebar.links.bots
                )
            rows += Nav(Nav.Kind.NEW_BOT, url = bots.newUrl)
            if (items.isEmpty()) {
                rows += NoBots(
                    bots.newUrl
                )
            } else {
                addBots(rows, items, current, needsYouTitle, quietTitle)
            }
            return rows
        }

        private fun addBots(
            rows: MutableList<SidebarRow>,
            items: List<Sidebar.Bot>,
            current: String?,
            needsYouTitle: String,
            quietTitle: String
        ) {
            val (needs, quiet) = items.partition { it.needsYou }
            listOf(needsYouTitle to needs, quietTitle to quiet).forEach { (title, group) ->
                if (group.isEmpty()) return@forEach
                if (needs.isNotEmpty() && quiet.isNotEmpty()) rows += Section(title)
                group.forEach { rows += BotRow(it, selected = isCurrent(it, current)) }
            }
        }

        private fun decisions(sidebar: Sidebar, current: String?) =
            Decisions(sidebar.waiting.count, selected = isDecisions(sidebar, current))

        /** Whether [current] is the Decisions page, on any decision (`?decision=`), as the web marks it. */
        fun isDecisions(sidebar: Sidebar, current: String?): Boolean {
            val url = (sidebar.waiting.url ?: sidebar.links.decisions)?.substringBefore('?')?.substringBefore('#')
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() }
            return url != null && path != null && (path == url || path.startsWith("$url/"))
        }

        /** Whether [current] is [bot]'s page or one under it (its chats, its settings), as the web marks it. */
        fun isCurrent(bot: Sidebar.Bot, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            val url = bot.url.substringBefore('?').substringBefore('#')
            return path == url || path.startsWith("$url/")
        }

        /** Whether [current] is [url]'s page, its query aside, as the web marks New chat and New bot. */
        fun isPage(url: String?, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            return url != null && path == url.substringBefore('?').substringBefore('#')
        }

        /** Whether [current] (a full URL, maybe with a query or anchor) is [chat]'s page. */
        fun isCurrent(chat: Sidebar.Chat, current: String?): Boolean {
            val path = current?.let { runCatching { java.net.URI(it).rawPath }.getOrNull() } ?: return false
            return path == chat.url.substringBefore('?').substringBefore('#')
        }
    }
}
