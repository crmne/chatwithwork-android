package com.chatwithwork.app.sidebar

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The sidebar page's rules (docs/server-contract.md, "The sidebar page"). */
class SidebarPageTest {
    private val base = AppConfig.baseUrl

    @Test
    fun `its web view says it's the app but lists no bridge components`() {
        val agent =
            SidebarPage.userAgent(
                AppConfig.userAgentPrefix("1.3.0", 10300),
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
            )
        assertThat(agent).startsWith(
            "Chat with Work; platform=android; version=1.3.0; build=10300; new-chat=page; Mozilla/5.0"
        )
        assertThat(agent).doesNotContain("bridge-components")
        assertThat(agent).doesNotContain("Hotwire Native")
        assertThat(agent).doesNotContain("Turbo Native")
    }

    @Test
    fun `reads the page's messages and nothing else`() {
        assertThat(SidebarPage.parse("""{"event":"ready"}""")).isEqualTo(SidebarPage.Message.Ready)
        assertThat(SidebarPage.parse("""{"event":"close"}""")).isEqualTo(SidebarPage.Message.Close)
        assertThat(SidebarPage.parse("""{"event":"visit","url":"$base/1/chats/2"}"""))
            .isEqualTo(SidebarPage.Message.Visit("$base/1/chats/2"))
        assertThat(
            SidebarPage.parse(
                """{"event":"confirm","id":"3","title":"Delete this chat?","description":null,""" +
                    """"confirm":"Delete","dismiss":"Cancel","destructive":true}"""
            )
        ).isEqualTo(SidebarPage.Message.Confirm("3", "Delete this chat?", null, "Delete", "Cancel", true))
        assertThat(SidebarPage.parse("""{"event":"visit"}""")).isNull()
        assertThat(SidebarPage.parse("""{"event":"confirm","title":"No id"}""")).isNull()
        assertThat(SidebarPage.parse("""{"event":"launch"}""")).isNull()
        assertThat(SidebarPage.parse("not json")).isNull()
    }

    @Test
    fun `only the page loads in the drawer, other pages go to the navigator and other sites to the browser`() {
        val page = "/1000001/sidebar"
        assertThat(SidebarPage.load("$base/1000001/sidebar", page)).isEqualTo(SidebarPage.Load.ALLOW)
        assertThat(SidebarPage.load("$base/1000001/sidebar?x=1", page)).isEqualTo(SidebarPage.Load.ALLOW)
        assertThat(SidebarPage.load("$base/1000001/chats/4", page)).isEqualTo(SidebarPage.Load.VISIT)
        assertThat(SidebarPage.load("$base/2000002/sidebar", page)).isEqualTo(SidebarPage.Load.VISIT)
        assertThat(SidebarPage.load("$base/users/sign_in", page)).isEqualTo(SidebarPage.Load.VISIT)
        assertThat(SidebarPage.load("https://support.chatwithwork.com/", page)).isEqualTo(SidebarPage.Load.EXTERNAL)
        assertThat(SidebarPage.load("mailto:hi@example.com", page)).isEqualTo(SidebarPage.Load.EXTERNAL)
    }

    @Test
    fun `the page is what the latest connect named, else what this organization named last time`() {
        assertThat(SidebarPage.pageFor(null, remembered = "/1/sidebar")).isEqualTo("/1/sidebar")
        assertThat(SidebarPage.pageFor(null, remembered = null)).isNull()
        assertThat(SidebarPage.pageFor(SidebarPage.Connect(page = "/2/sidebar"), remembered = "/1/sidebar"))
            .isEqualTo("/2/sidebar")
        // An older server's connect names none: the native sidebar, whatever was remembered.
        assertThat(SidebarPage.pageFor(SidebarPage.Connect(), remembered = "/1/sidebar")).isNull()
        assertThat(SidebarPage.pageFor(SidebarPage.Connect(page = "//evil.example/x"), remembered = null)).isNull()
    }

    @Test
    fun `scripts quote what they pass`() {
        assertThat(SidebarPage.Script.current("$base/1/chats/2", null))
            .isEqualTo("window.chatWithWorkSidebar && window.chatWithWorkSidebar.current(\"$base/1/chats/2\", null)")
        assertThat(SidebarPage.Script.current("x\"); alert(1); (\"", "/1/bots/3"))
            .contains("\"x\\\"); alert(1); (\\\"\", \"/1/bots/3\"")
        assertThat(SidebarPage.Script.answer("7", true))
            .isEqualTo("window.chatWithWorkSidebar && window.chatWithWorkSidebar.answer(\"7\", true)")
        // A page from before closed() has nothing to call.
        assertThat(SidebarPage.Script.CLOSED).isEqualTo(
            "window.chatWithWorkSidebar && window.chatWithWorkSidebar.closed && window.chatWithWorkSidebar.closed()"
        )
    }
}
