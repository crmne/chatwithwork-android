package com.chatwithwork.app.sidebar

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The sidebar's JSON as docs/server-contract.md describes it, and the rows it makes. */
class SidebarTest {
    private val base = AppConfig.baseUrl

    private val json =
        """
        {
          "organization": {"id": "482139075", "name": "Northwind Studio", "initials": "NS"},
          "organizations": [{"id": "482139075", "name": "Northwind Studio", "initials": "NS", "current": true, "role": "admin", "url": "/482139075/chats/new"}],
          "person": {"name": "Maya Hart", "email": "maya@northwind.example", "initials": "MH", "avatar_url": null},
          "list": {"key": "3", "name": "Website relaunch", "kind": "project", "hq": false},
          "lists": [{"key": "recent", "name": "Private chats", "kind": "recent", "hq": false, "pinned": false, "current": false},
                    {"key": "3", "name": "Website relaunch", "kind": "project", "current": true}],
          "links": {"new_chat": "/482139075/chats/new?project_id=3", "bots": "/482139075/bots", "settings": "/482139075/settings",
                    "projects": "/482139075/projects", "new_project": null, "organizations": "/accounts", "new_organization": "/accounts/new"},
          "waiting": {"count": 1, "items": [{"title": "Launch date in #launch", "url": "/482139075/chats/42", "kind": "approval", "project": "Website relaunch"}]},
          "bots": {"count": 0, "attention": false, "next_run": "08:30"},
          "pins": [{"number": 7, "title": "Q3 launch plan", "url": "/482139075/chats/7", "state": "idle", "waiting": false, "project": null, "asked_at": "2026-10-20T08:00:00Z", "group": "Previous 7 days"}],
          "chats": [
            {"number": 42, "title": "Launch date in #launch", "url": "/482139075/chats/42", "state": "idle", "waiting": true, "project": "Website relaunch", "asked_at": "2026-10-25T08:12:00Z", "group": "Today"},
            {"number": 41, "title": "Lisbon offsite budget", "url": "/482139075/chats/41", "state": "processing", "group": "Today", "something_new": 1},
            {"number": 40, "title": "Per-person column in euros", "url": "/482139075/chats/40", "group": "Yesterday"}
          ],
          "next": "/482139075/native/sidebar?list=3&before=40&asked=2026-10-24T09:00:00.000000Z"
        }
        """.trimIndent()

    private val sidebar = Sidebar.parse(json)

    @Test
    fun `reads the contract's JSON, ignoring keys it doesn't know`() {
        assertThat(sidebar.organization.id).isEqualTo("482139075")
        assertThat(sidebar.person?.initials).isEqualTo("MH")
        assertThat(sidebar.list?.isProject).isTrue()
        assertThat(sidebar.links.newChat).isEqualTo("/482139075/chats/new?project_id=3")
        assertThat(sidebar.links.newProject).isNull()
        assertThat(sidebar.waiting.items.single().kind).isEqualTo("approval")
        assertThat(sidebar.bots?.nextRun).isEqualTo("08:30")
        assertThat(sidebar.chats.map { it.number }).containsExactly(42L, 41L, 40L).inOrder()
        assertThat(sidebar.anyProcessing).isTrue()
    }

    @Test
    fun `a guest's sidebar has no bots and no project links`() {
        val guest = Sidebar.parse(
            """{"organization": {"id": "482139075", "name": "Northwind"}, "bots": null, "links": {"bots": null}}"""
        )
        assertThat(guest.bots).isNull()
        assertThat(guest.links.bots).isNull()
        val rows = SidebarRow.build(guest, "", null, "Pinned", botsKnown = true)
        assertThat(rows.filterIsInstance<SidebarRow.Nav>().map { it.kind })
            .containsExactly(SidebarRow.Nav.Kind.NEW_CHAT)
        assertThat(rows.filterIsInstance<SidebarRow.Views>()).isEmpty()
        // Guests have no switch, but Decisions still leads.
        assertThat(rows.first()).isEqualTo(SidebarRow.Decisions(0, selected = false))
        assertThat(rows.last()).isEqualTo(SidebarRow.Empty(SidebarRow.Empty.Kind.NO_CHATS))
    }

    @Test
    fun `rows run Decisions, switcher, search, actions, pins, then chats by day`() {
        val rows = SidebarRow.build(sidebar, "", "$base/482139075/chats/41?x=1", "Pinned", botsKnown = true)
        assertThat(rows[0]).isEqualTo(SidebarRow.Decisions(1, selected = false))
        assertThat(rows[1]).isEqualTo(SidebarRow.Switcher(sidebar.list!!))
        assertThat(rows[2]).isEqualTo(SidebarRow.Search)
        assertThat(rows[3]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.NEW_CHAT))
        assertThat(rows[4]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.BOTS, meta = "08:30", attention = false))
        assertThat(rows.filterIsInstance<SidebarRow.Section>().map { it.title })
            .containsExactly("Pinned", "Today", "Yesterday")
            .inOrder()
        val selected = rows.filterIsInstance<SidebarRow.ChatRow>().filter { it.selected }
        assertThat(selected.map { it.chat.number }).containsExactly(41L)
    }

    @Test
    fun `searching filters by every word and hides the actions`() {
        val rows = SidebarRow.build(sidebar, "  LAUNCH  plan ", null, "Pinned", botsKnown = true)
        assertThat(rows.filterIsInstance<SidebarRow.Nav>()).isEmpty()
        assertThat(rows.first()).isEqualTo(SidebarRow.Decisions(1, selected = false))
        assertThat(rows.filterIsInstance<SidebarRow.ChatRow>().map { it.chat.number }).containsExactly(7L)

        val none = SidebarRow.build(sidebar, "zebra", null, "Pinned", botsKnown = true)
        assertThat(none.last()).isEqualTo(SidebarRow.Empty(SidebarRow.Empty.Kind.NO_MATCHES))
    }

    @Test
    fun `without the server's sidebar it still offers New chat and Bots`() {
        assertThat(SidebarRow.build(null, "", null, "Pinned", botsKnown = true))
            .containsExactly(SidebarRow.Nav(SidebarRow.Nav.Kind.NEW_CHAT), SidebarRow.Nav(SidebarRow.Nav.Kind.BOTS))
        assertThat(SidebarRow.build(null, "", null, "Pinned", botsKnown = false))
            .containsExactly(SidebarRow.Nav(SidebarRow.Nav.Kind.NEW_CHAT))
    }

    @Test
    fun `the next page adds the chats it doesn't have yet`() {
        val next = Sidebar.parse(
            """{"organization": {"id": "482139075", "name": "N"}, "chats": [
               {"number": 40, "title": "Per-person column in euros", "url": "/482139075/chats/40"},
               {"number": 39, "title": "Contract renewals", "url": "/482139075/chats/39"}], "next": null}"""
        )
        val both = sidebar.plus(next)
        assertThat(both.chats.map { it.number }).containsExactly(42L, 41L, 40L, 39L).inOrder()
        assertThat(both.next).isNull()
    }

    @Test
    fun `reads a chat's long-press actions and the Decisions page, which older servers leave out`() {
        val actions =
            """
            [{"key": "rename", "title": "Rename", "method": "get", "url": "/482139075/chats/40/edit",
              "androidImage": "edit"},
             {"key": "delete", "title": "Delete", "method": "delete", "url": "/482139075/chats/40",
              "iosImage": "trash", "androidImage": "delete", "confirm": "Delete this chat?", "destructive": true}]
            """.trimIndent()
        val withActions = Sidebar.parse(
            json
                .replace(
                    "\"waiting\": {\"count\": 1,",
                    "\"waiting\": {\"count\": 1, \"url\": \"/482139075/decisions\","
                )
                .replace("\"group\": \"Yesterday\"}", "\"group\": \"Yesterday\", \"actions\": $actions}")
        )
        assertThat(withActions.waiting.url).isEqualTo("/482139075/decisions")
        val read = withActions.chats.last().actions
        assertThat(read.map { it.key }).containsExactly("rename", "delete").inOrder()
        assertThat(read[0].opensPage).isTrue()
        assertThat(read[1].opensPage).isFalse()
        assertThat(read[1].confirm).isEqualTo("Delete this chat?")
        assertThat(read[1].destructive).isTrue()

        assertThat(sidebar.waiting.url).isNull()
        assertThat(sidebar.chats.first().actions).isEmpty()
    }

    @Test
    fun `Decisions shows how many wait, and is marked on the Decisions page and any decision on it`() {
        val withPage = Sidebar.parse(
            json.replace(
                "\"waiting\": {\"count\": 1,",
                "\"waiting\": {\"count\": 1, \"url\": \"/482139075/decisions\","
            )
        )
        fun decisions(current: String?) =
            SidebarRow.build(withPage, "", current, "Pinned", botsKnown = true).first() as SidebarRow.Decisions
        assertThat(decisions("$base/482139075/chats/42")).isEqualTo(SidebarRow.Decisions(1, selected = false))
        assertThat(decisions("$base/482139075/decisions").selected).isTrue()
        assertThat(decisions("$base/482139075/decisions?decision=approval-7").selected).isTrue()
        assertThat(decisions("$base/482139075/decisions_old").selected).isFalse()
        // An older server without the page marks nothing.
        assertThat(SidebarRow.isDecisions(sidebar, "$base/482139075/decisions")).isFalse()

        val none = withPage.copy(waiting = withPage.waiting.copy(count = 0, items = emptyList()))
        assertThat(SidebarRow.build(none, "", null, "Pinned", botsKnown = true).first())
            .isEqualTo(SidebarRow.Decisions(0, selected = false))
    }

    private val sections =
        """
        "sections": [
          {"id": 7, "name": "Customers", "collapsed": false, "edit_url": "/482139075/sidebar/sections/7/edit",
           "collapse_url": "/482139075/sidebar/sections/7/collapse", "position_url": "/482139075/sidebar/sections/7/position",
           "chats": [{"number": 37, "title": "Initech renewal", "url": "/482139075/chats/37", "state": "processing", "group": "Today"}]},
          {"id": 9, "name": "Hiring", "collapsed": true, "edit_url": "/482139075/sidebar/sections/9/edit",
           "collapse_url": "/482139075/sidebar/sections/9/collapse",
           "chats": [{"number": 36, "title": "Designer shortlist", "url": "/482139075/chats/36", "group": "Yesterday"}]}
        ],
        """.trimIndent()

    private val withSections = Sidebar.parse(json.replace("\"chats\": [", "$sections\n\"chats\": ["))

    @Test
    fun `reads the person's sections, which older servers leave out`() {
        assertThat(withSections.sections.map { it.name }).containsExactly("Customers", "Hiring").inOrder()
        assertThat(withSections.sections[1].collapsed).isTrue()
        assertThat(withSections.sections[0].editUrl).isEqualTo("/482139075/sidebar/sections/7/edit")
        assertThat(withSections.chats.map { it.number }).containsExactly(42L, 41L, 40L).inOrder()
        assertThat(sidebar.sections).isEmpty()

        // An open section closes with a POST, a closed one opens with a DELETE.
        assertThat(withSections.sections[0].toggleAction?.method).isEqualTo("post")
        assertThat(withSections.sections[1].toggleAction?.method).isEqualTo("delete")
        assertThat(withSections.sections[1].toggleAction?.url).isEqualTo("/482139075/sidebar/sections/9/collapse")
        assertThat(withSections.sections[1].toggleAction?.opensPage).isFalse()

        val opened = withSections.withCollapsed(9, false)
        assertThat(opened.sections.map { it.collapsed }).containsExactly(false, false).inOrder()
        assertThat(withSections.plus(withSections).sections).isEqualTo(withSections.sections)
    }

    @Test
    fun `sections follow the pins, a closed one hides its chats, and the rest go under Other chats`() {
        val rows = SidebarRow.build(withSections, "", null, "Pinned", botsKnown = true, otherTitle = "Other chats")
        val after = rows.dropWhile { it !is SidebarRow.Section }
        assertThat(
            after.map { row ->
                when (row) {
                    is SidebarRow.Section -> row.title
                    is SidebarRow.ChatSection -> "[${row.section.name}]"
                    is SidebarRow.ChatRow -> row.chat.number.toString()
                    else -> row.toString()
                }
            }
        ).containsExactly("Pinned", "7", "[Customers]", "37", "[Hiring]", "Other chats", "42", "41", "40").inOrder()

        // A search finds chats in sections too, closed ones included.
        val found = SidebarRow.build(withSections, "shortlist", null, "Pinned", botsKnown = true)
        assertThat(found.filterIsInstance<SidebarRow.ChatRow>().map { it.chat.number }).containsExactly(36L)
        assertThat(found.filterIsInstance<SidebarRow.Empty>()).isEmpty()

        // A chat answering in a section keeps the sidebar fresh.
        val idle = Sidebar.parse(json.replace("\"state\": \"processing\", ", ""))
        assertThat(idle.anyProcessing).isFalse()
        assertThat(idle.copy(sections = withSections.sections).anyProcessing).isTrue()
    }

    private val botsJson =
        """
        "bots": {"count": 1, "attention": true, "next_run": null, "url": "/482139075/bots", "new_url": "/482139075/bots/new",
                 "items": [
                   {"id": 5, "name": "Support triage", "url": "/482139075/bots/5", "group": "needs_you", "project": "Support",
                    "state": "waiting", "state_label": "Waiting for you", "tone": "attention", "waiting": 2, "working": true, "next_run": null},
                   {"id": 3, "name": "Morning briefing", "url": "/482139075/bots/3", "group": "quiet", "project": null,
                    "state": "next", "state_label": "Next 08:30", "tone": "positive", "waiting": 0, "working": false, "next_run": "08:30"},
                   {"id": 4, "name": "Weekly report", "url": "/482139075/bots/4", "group": "quiet",
                    "state": "paused", "state_label": "Paused", "tone": null, "waiting": 0, "working": false}
                 ]}
        """.trimIndent()

    private val withBots = Sidebar.parse(
        json.replace(""""bots": {"count": 0, "attention": false, "next_run": "08:30"}""", botsJson)
    )

    @Test
    fun `reads the person's bots, which older servers leave out`() {
        val bots = withBots.bots!!
        assertThat(bots.url).isEqualTo("/482139075/bots")
        assertThat(bots.newUrl).isEqualTo("/482139075/bots/new")
        assertThat(bots.items!!.map { it.id }).containsExactly(5L, 3L, 4L).inOrder()
        val triage = bots.items!!.first()
        assertThat(triage.needsYou).isTrue()
        assertThat(triage.project).isEqualTo("Support")
        assertThat(triage.stateLabel).isEqualTo("Waiting for you")
        assertThat(triage.waiting).isEqualTo(2)
        assertThat(bots.items!!.last().tone).isNull()
        assertThat(sidebar.bots?.items).isNull()

        // A bot at work keeps the sidebar fresh, like a chat answering.
        val idle = withBots.copy(chats = sidebar.chats.map { it.copy(state = "idle") })
        assertThat(idle.anyProcessing).isTrue()
        assertThat(idle.copy(bots = bots.copy(items = bots.items!!.map { it.copy(working = false) })).anyProcessing)
            .isFalse()
    }

    @Test
    fun `an older server keeps the Bots row, a newer one puts the switch under Decisions`() {
        val old = SidebarRow.build(sidebar, "", null, "Pinned", botsKnown = true, view = SidebarRow.View.BOTS)
        assertThat(old.filterIsInstance<SidebarRow.Views>()).isEmpty()
        assertThat(old[4]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.BOTS, meta = "08:30", attention = false))

        val chats = SidebarRow.build(withBots, "", null, "Pinned", botsKnown = true)
        assertThat(chats[0]).isInstanceOf(SidebarRow.Decisions::class.java)
        assertThat(chats[1]).isEqualTo(SidebarRow.Views(SidebarRow.View.CHATS, botsAttention = true))
        assertThat(chats[2]).isEqualTo(SidebarRow.Switcher(withBots.list!!))
        assertThat(chats.filterIsInstance<SidebarRow.Nav>().map { it.kind })
            .containsExactly(SidebarRow.Nav.Kind.NEW_CHAT)
        assertThat(chats.filterIsInstance<SidebarRow.ChatRow>()).isNotEmpty()

        // Both stay at the top while searching, above the field.
        val search = SidebarRow.build(withBots, "launch", null, "Pinned", botsKnown = true)
        assertThat(search[0]).isInstanceOf(SidebarRow.Decisions::class.java)
        assertThat(search[1]).isEqualTo(SidebarRow.Views(SidebarRow.View.CHATS, botsAttention = true))
    }

    @Test
    fun `the Bots half draws Decisions, the switch, the card, New bot, then the bots by group`() {
        val rows = SidebarRow.build(
            withBots,
            "",
            "$base/482139075/bots/3/chats?x=1",
            "Pinned",
            botsKnown = true,
            view = SidebarRow.View.BOTS
        )
        assertThat(rows[0]).isInstanceOf(SidebarRow.Decisions::class.java)
        assertThat(rows[1]).isEqualTo(SidebarRow.Views(SidebarRow.View.BOTS, botsAttention = true))
        assertThat(rows[2]).isEqualTo(
            SidebarRow.BotsCard("Northwind Studio", count = 3, working = 1, waiting = 2, url = "/482139075/bots")
        )
        assertThat(rows[3]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.NEW_BOT, url = "/482139075/bots/new"))
        assertThat(rows.drop(4).map { (it as? SidebarRow.Section)?.title ?: (it as SidebarRow.BotRow).bot.id })
            .containsExactly("Needs you", 5L, "Quiet", 3L, 4L)
            .inOrder()
        assertThat(rows.filterIsInstance<SidebarRow.BotRow>().filter { it.selected }.map { it.bot.id })
            .containsExactly(3L)
        assertThat(rows).doesNotContain(SidebarRow.Search)
        assertThat(rows.filterIsInstance<SidebarRow.ChatRow>()).isEmpty()

        // One group alone has no heading; no bots at all says where they'll show.
        val bots = withBots.bots!!
        val quiet = withBots.copy(bots = bots.copy(items = bots.items!!.drop(1)))
        val quietRows = SidebarRow.build(quiet, "", null, "Pinned", botsKnown = true, view = SidebarRow.View.BOTS)
        assertThat(quietRows.filterIsInstance<SidebarRow.Section>()).isEmpty()
        assertThat(quietRows.filterIsInstance<SidebarRow.BotRow>()).hasSize(2)
        val none = withBots.copy(bots = bots.copy(items = emptyList()))
        val noneRows = SidebarRow.build(none, "", null, "Pinned", botsKnown = true, view = SidebarRow.View.BOTS)
        assertThat(noneRows.last()).isEqualTo(SidebarRow.NoBots("/482139075/bots/new"))
        assertThat(noneRows.filterIsInstance<SidebarRow.BotsCard>().single().count).isEqualTo(0)
    }

    @Test
    fun `a guest has no switch, whichever half was chosen`() {
        val guest = withBots.copy(bots = null)
        val rows = SidebarRow.build(guest, "", null, "Pinned", botsKnown = true, view = SidebarRow.View.BOTS)
        assertThat(rows.filterIsInstance<SidebarRow.Views>()).isEmpty()
        assertThat(rows.filterIsInstance<SidebarRow.ChatRow>()).isNotEmpty()
    }

    @Test
    fun `reads who asks for each decision, which older servers leave out`() {
        val asked = Sidebar.parse(
            json.replace(
                """"kind": "approval", "project": "Website relaunch"}""",
                """"kind": "bot", "project": "Website relaunch", "summary": "Send the refund?",
                   "asker": {"kind": "bot", "name": "Front desk", "project": "Website relaunch"}}"""
            )
        )
        val item = asked.waiting.items.single()
        assertThat(item.asker).isEqualTo(Sidebar.Asker("bot", "Front desk", "Website relaunch"))
        assertThat(item.summary).isEqualTo("Send the refund?")
        assertThat(sidebar.waiting.items.single().asker).isNull()
    }

    @Test
    fun `the chosen half reads back from what the app remembered`() {
        assertThat(SidebarRow.View.from("bots")).isEqualTo(SidebarRow.View.BOTS)
        assertThat(SidebarRow.View.from("chats")).isEqualTo(SidebarRow.View.CHATS)
        assertThat(SidebarRow.View.from(null)).isEqualTo(SidebarRow.View.CHATS)
        assertThat(SidebarRow.View.from("something else")).isEqualTo(SidebarRow.View.CHATS)
    }

    @Test
    fun `a bot's page and the pages under it mark its row`() {
        val bot = withBots.bots!!.items!![1]
        assertThat(SidebarRow.isCurrent(bot, "$base/482139075/bots/3")).isTrue()
        assertThat(SidebarRow.isCurrent(bot, "$base/482139075/bots/3/setup#when")).isTrue()
        assertThat(SidebarRow.isCurrent(bot, "$base/482139075/bots/31")).isFalse()
        assertThat(SidebarRow.isCurrent(bot, null)).isFalse()
    }

    @Test
    fun `a project's mark is its initials`() {
        assertThat(SidebarDrawer.initialsOf("Website relaunch")).isEqualTo("WR")
        assertThat(SidebarDrawer.initialsOf("hq")).isEqualTo("H")
    }
}
