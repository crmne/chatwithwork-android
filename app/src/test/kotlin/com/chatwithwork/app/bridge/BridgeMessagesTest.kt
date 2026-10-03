package com.chatwithwork.app.bridge

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The messages in docs/server-contract.md, decoded the way Hotwire Native
 * decodes them (unknown keys ignored, missing ones null), so a Stimulus
 * controller that follows the contract always reaches the component.
 */
class BridgeMessagesTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `button reads Joe Masilotti's payload and the stable action name`() {
        val stock =
            json.decodeFromString<ButtonComponent.Data>(
                """{"title":"Pin","iosImage":"pin","androidImage":"keep","color":null,"metadata":{"url":"https://x"}}"""
            )
        assertThat(stock.title).isEqualTo("Pin")
        assertThat(stock.image).isEqualTo("keep")
        assertThat(stock.nativeAction).isNull()

        // The title may be translated; native code goes by nativeAction.
        val named =
            json.decodeFromString<ButtonComponent.Data>(
                """{"title":"Neuer Chat","androidImage":"edit_square","nativeAction":"new-chat"}"""
            )
        assertThat(named.nativeAction).isEqualTo("new-chat")
    }

    @Test
    fun `menu items may be checked or disabled`() {
        val data =
            json.decodeFromString<MenuComponent.Data>(
                """{"items":[{"title":"Rename","androidImage":"edit"},{"title":"Delete","destructive":true,"disabled":true},{"title":"Pinned","checked":true}],"side":"right"}"""
            )
        assertThat(data.items.map { it.title }).containsExactly("Rename", "Delete", "Pinned").inOrder()
        assertThat(data.items[1].destructive).isTrue()
        assertThat(data.items[1].disabled).isTrue()
        assertThat(data.items[2].checked).isTrue()
    }

    @Test
    fun `the organization switcher is a chooser and a page's actions are not`() {
        // What the menu controller sends for the switcher on /settings, and for a chat.
        val switcher =
            json.decodeFromString<MenuComponent.Data>(
                """{"items":[{"title":"Acme","checked":true},{"title":"Initech","checked":false}],"side":"left","label":"Acme","iosImage":"building.2","header":"Organizations"}"""
            )
        assertThat(switcher.isChooser).isTrue()
        assertThat(switcher.label).isEqualTo("Acme")

        val chat =
            json.decodeFromString<MenuComponent.Data>(
                """{"items":[{"title":"Pin","androidImage":"keep","checked":false,"nativeAction":"pin"}],"label":"Chat options"}"""
            )
        assertThat(chat.isChooser).isFalse()
        assertThat(chat.items.single().nativeAction).isEqualTo("pin")

        // A header alone makes a chooser too, as on iOS.
        val withHeader =
            json.decodeFromString<MenuComponent.Data>("""{"items":[{"title":"Acme"}],"header":"Organizations"}""")
        assertThat(withHeader.isChooser).isTrue()
    }

    @Test
    fun `context menu reads the iOS payload, anchor included`() {
        val data =
            json.decodeFromString<ContextMenuComponent.Data>(
                """{"items":[{"title":"Copy","iosImage":"doc.on.doc","androidImage":"content_copy"}],"rect":{"x":12.5,"y":300,"width":32,"height":32},"scroll":{"x":0,"y":120},"title":"Answer"}"""
            )
        assertThat(data.rect?.x).isEqualTo(12.5)
        assertThat(data.items.single().image).isEqualTo("content_copy")
        assertThat(data.title).isEqualTo("Answer")

        // A long press has no element to anchor to: no rect means a bottom sheet.
        assertThat(json.decodeFromString<ContextMenuComponent.Data>("""{"items":[]}""").rect).isNull()
    }

    @Test
    fun `context menu copy items may carry the answer as html`() {
        val items =
            json.decodeFromString<ContextMenuComponent.Data>(
                """{"items":[{"title":"Copy","copy":"**Hi**","copyHtml":"<p><strong>Hi</strong></p>"},{"title":"Copy","copy":"Plain"},{"title":"Retry"}]}"""
            ).items
        assertThat(items[0].copyText).isEqualTo("**Hi**")
        assertThat(items[0].copyHtml).isEqualTo("<p><strong>Hi</strong></p>")
        // Older payloads, and readers who copy Markdown, send no html.
        assertThat(items[1].copyText).isEqualTo("Plain")
        assertThat(items[1].copyHtml).isNull()
        assertThat(items[2].copyText).isNull()
    }

    @Test
    fun `auth session takes a url and replies with the callback or an error`() {
        val request = json.decodeFromString<AuthSessionComponent.Request>(
            """{"url":"/native/handoffs/abc","ephemeral":true}"""
        )
        assertThat(request.url).isEqualTo("/native/handoffs/abc")
        assertThat(request.ephemeral).isTrue()

        val success = json.encodeToString(AuthSessionComponent.Reply(url = "chatwithwork://auth/callback?token=t"))
        assertThat(success).isEqualTo("""{"url":"chatwithwork://auth/callback?token=t"}""")
        assertThat(
            json.encodeToString(AuthSessionComponent.Reply(error = "canceled"))
        ).isEqualTo("""{"error":"canceled"}""")
    }

    @Test
    fun `notification token replies with the iOS fields`() {
        val reply =
            json.encodeToString(
                NotificationTokenComponent.Reply(
                    status = "authorized",
                    token = "fcm-token",
                    appId = "com.chatwithwork.app"
                )
            )
        val fields = json.parseToJsonElement(reply).jsonObject
        assertThat(fields.keys).containsExactly("status", "token", "platform", "environment", "appId")
        assertThat(fields["platform"]?.jsonPrimitive?.content).isEqualTo("android")
        assertThat(fields["environment"]?.jsonPrimitive?.content).isEqualTo("production")

        val noToken = json.encodeToString(NotificationTokenComponent.Reply(status = "not_determined", appId = "x"))
        assertThat(json.parseToJsonElement(noToken).jsonObject.keys).doesNotContain("token")
    }

    @Test
    fun `toast takes the Rails flash type`() {
        val data = json.decodeFromString<ToastComponent.Data>("""{"message":"Connected Slack","type":"notice"}""")
        assertThat(data.type).isEqualTo("notice")
    }

    @Test
    fun `share replies whether something was shared`() {
        assertThat(json.encodeToString(ShareComponent.Result(completed = true, activityType = "com.google.android.gm")))
            .isEqualTo("""{"completed":true,"activityType":"com.google.android.gm"}""")
        assertThat(json.encodeToString(ShareComponent.Result(completed = false))).isEqualTo("""{"completed":false}""")
    }

    @Test
    fun `alert defaults its buttons`() {
        val data = json.decodeFromString<AlertComponent.Data>("""{"title":"Delete this chat?","destructive":true}""")
        assertThat(data.confirm).isNull()
        assertThat(data.dismiss).isNull()
        assertThat(data.destructive).isTrue()
    }
}
