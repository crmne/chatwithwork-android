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
            .containsExactly(SidebarRow.Nav.Kind.NEW_CHAT, SidebarRow.Nav.Kind.WAITING)
            .inOrder()
        assertThat(rows.last()).isEqualTo(SidebarRow.Empty(SidebarRow.Empty.Kind.NO_CHATS))
    }

    @Test
    fun `rows run switcher, search, actions, pins, then chats by day`() {
        val rows = SidebarRow.build(sidebar, "", "$base/482139075/chats/41?x=1", "Pinned", botsKnown = true)
        assertThat(rows[0]).isEqualTo(SidebarRow.Switcher(sidebar.list!!))
        assertThat(rows[1]).isEqualTo(SidebarRow.Search)
        assertThat(rows[2]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.NEW_CHAT))
        assertThat(rows[3]).isEqualTo(SidebarRow.Nav(SidebarRow.Nav.Kind.WAITING, meta = "1", attention = true))
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
    fun `a project's mark is its initials`() {
        assertThat(SidebarDrawer.initialsOf("Website relaunch")).isEqualTo("WR")
        assertThat(SidebarDrawer.initialsOf("hq")).isEqualTo("H")
    }
}
