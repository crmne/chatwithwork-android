package com.chatwithwork.app.shortcuts

import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.main.LaunchLinks
import com.chatwithwork.app.routing.AppUrls
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** The icon's long press and the assistant: where they lead, and what they may not do. */
class ShortcutsTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val base = AppConfig.baseUrl

    private fun document(path: String) = DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    private fun Element.elements(name: String) =
        getElementsByTagName(name).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun strings() = document("src/main/res/values/strings.xml")
        .documentElement
        .elements("string")
        .associate { it.getAttribute("name") to it.textContent }

    private val shortcuts get() = document("src/main/res/xml/shortcuts.xml").documentElement.elements("shortcut")

    @Test
    fun `the three shortcuts carry the names the iOS app uses, in its order`() {
        val names = shortcuts.map {
            strings()[it.getAttributeNS(android, "shortcutShortLabel").removePrefix("@string/")]
        }

        assertThat(names).containsExactly("New chat", "New bot", "Decisions").inOrder()
    }

    @Test
    fun `each shortcut leads through ShortcutActivity, so the launcher's clearing leaves the app's task alone`() {
        val intents = shortcuts.map { it.elements("intent").single() }

        assertThat(intents.map { it.getAttributeNS(android, "action") })
            .containsExactly(LaunchLinks.ACTION_NEW_CHAT, LaunchLinks.ACTION_NEW_BOT, LaunchLinks.ACTION_DECISIONS)
            .inOrder()
        intents.forEach {
            assertThat(it.getAttributeNS(android, "targetClass")).isEqualTo(ShortcutActivity::class.java.name)
            assertThat(it.getAttributeNS(android, "targetPackage")).isEqualTo("com.chatwithwork.app")
        }
    }

    @Test
    fun `debug and staging differ only by their application id, which Android reads as written`() {
        val release = File("src/main/res/xml/shortcuts.xml").readText()
        listOf("debug", "staging").forEach { build ->
            assertThat(File("src/$build/res/xml/shortcuts.xml").readText())
                .isEqualTo(release.replace("\"com.chatwithwork.app\"", "\"com.chatwithwork.app.$build\""))
        }
    }

    @Test
    fun `the assistant's filter asks for nothing beyond the activity`() {
        val manifest = document("src/main/AndroidManifest.xml").documentElement
        val filter =
            manifest.elements("intent-filter").single { filter ->
                filter.elements("action").any { it.getAttributeNS(android, "name") == "android.intent.action.ASSIST" }
            }

        assertThat(filter.elements("category").map { it.getAttributeNS(android, "name") })
            .containsExactly("android.intent.category.DEFAULT")
        assertThat((filter.parentNode as Element).getAttributeNS(android, "name")).isEqualTo(".main.MainActivity")
        assertThat(manifest.elements("service").map { it.getAttributeNS(android, "permission") })
            .doesNotContain("android.permission.BIND_VOICE_INTERACTION")
    }

    @Test
    fun `the pages are the organization's, or the server's pick when none is known`() {
        assertThat(AppUrls.newBot("482139075")).isEqualTo("$base/482139075/bots/new")
        assertThat(AppUrls.newBot(null)).isEqualTo("$base/bots/new")
        assertThat(AppUrls.decisions("482139075")).isEqualTo("$base/482139075/decisions")
        assertThat(AppUrls.decisions(null)).isEqualTo("$base/decisions")
    }

    @Test
    fun `asking for the focus waits for a new chat, lasts one composer, and never asks for dictation`() {
        val newChat = "$base/482139075/chats/new"
        PendingShares.takeDictation()
        PendingShares.takeFocus(newChat)
        PendingShares.askFocus()

        assertThat(PendingShares.takeDictation()).isFalse()
        assertThat(PendingShares.takeFocus("$base/482139075/chats/42")).isFalse()
        assertThat(PendingShares.takeFocus(newChat)).isTrue()
        assertThat(PendingShares.takeFocus(newChat)).isFalse()
    }
}
