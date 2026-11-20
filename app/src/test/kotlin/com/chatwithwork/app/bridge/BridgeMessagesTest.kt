package com.chatwithwork.app.bridge

import com.chatwithwork.app.composer.ComposerState
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The messages in docs/server-contract.md, decoded the way Hotwire Native
 * decodes them (unknown keys ignored, missing ones null), so a Stimulus
 * controller that follows the contract always reaches the component.
 */
class BridgeMessagesTest {
    @Test
    fun `composer overlay replies with dimensions in the same coordinate space`() {
        val payload = ComposerComponent.layoutReply(height = 366, width = 1206)
        assertThat(payload["action"]?.jsonPrimitive?.content).isEqualTo("layout")
        assertThat(payload["height"]?.jsonPrimitive?.content).isEqualTo("366")
        assertThat(payload["width"]?.jsonPrimitive?.content).isEqualTo("1206")
    }

    @Test
    fun `composer's Narrow services reply is the narrow action alone`() {
        val payload = ComposerComponent.narrowReply()
        assertThat(payload["action"]?.jsonPrimitive?.content).isEqualTo("narrow")
        assertThat(payload.keys).containsExactly("action")
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `composer accepts optional maker and viewer permission fields`() {
        val state = json.decodeFromString<ComposerState>(
            """{"models":[{"name":"Qwen","selected":true,"iconName":"qwen","invertIcon":true}],
                "permissionModes":{"selected":null,"options":[]},"metadata":{"url":"https://x"}}"""
        )
        assertThat(state.selectedModel?.iconName).isEqualTo("qwen")
        assertThat(state.permissionModes?.selected).isNull()
        assertThat(state.canSelectPermissionMode("everything")).isFalse()
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
    fun `file reads the files a viewer shares, the file first, then its PDF`() {
        val data =
            json.decodeFromString<FileComponent.Data>(
                """{"files":[{"url":"https://chatwithwork.com/rails/active_storage/blobs/proxy/abc/deck.pptx?disposition=attachment","name":"deck.pptx","contentType":"application/vnd.openxmlformats-officedocument.presentationml.presentation","title":"PowerPoint"},{"url":"/rails/active_storage/blobs/proxy/def/deck.pdf","name":"deck.pdf","contentType":"application/pdf","title":"PDF"}],"metadata":{"url":"https://x"}}"""
            )
        assertThat(data.files.map { it.name }).containsExactly("deck.pptx", "deck.pdf").inOrder()
        assertThat(data.files.first().title).isEqualTo("PowerPoint")

        val page = "https://chatwithwork.com/482139075/chats/42/files/xyz"
        assertThat(
            data.files[1].resolvedUrl(page)
        ).isEqualTo("https://chatwithwork.com/rails/active_storage/blobs/proxy/def/deck.pdf")
        assertThat(FileComponent.SharedFile(url = "javascript:alert(1)", name = "x").resolvedUrl(page)).isNull()

        val bare = json.decodeFromString<FileComponent.Data>("""{"files":[{"url":"/a.csv","name":"a.csv"}]}""")
        assertThat(bare.files.single().contentType).isNull()
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
        assertThat(fields.keys).containsExactly("status", "token", "platform", "environment", "appId", "needsKey")
        assertThat(fields["platform"]?.jsonPrimitive?.content).isEqualTo("android")
        assertThat(fields["environment"]?.jsonPrimitive?.content).isEqualTo("production")

        val noToken = json.encodeToString(NotificationTokenComponent.Reply(status = "not_determined", appId = "x"))
        assertThat(json.parseToJsonElement(noToken).jsonObject.keys).doesNotContain("token")
    }

    @Test
    fun `notification token hands over the phone's notification key and its token`() {
        val key =
            json.decodeFromString<NotificationTokenComponent.Key>(
                """{"key":"eyJ--abc","token":"fcm-token","metadata":{"url":"https://x"}}"""
            )
        assertThat(key).isEqualTo(NotificationTokenComponent.Key("eyJ--abc", "fcm-token"))
        val needs = json.encodeToString(
            NotificationTokenComponent.Reply(status = "authorized", token = "t", appId = "x", needsKey = true)
        )
        assertThat(json.parseToJsonElement(needs).jsonObject["needsKey"]?.jsonPrimitive?.content).isEqualTo("true")
    }

    @Test
    fun `notification token offers for a reason`() {
        assertThat(json.decodeFromString<NotificationTokenComponent.Offer>("""{"reason":"approval"}""").reason)
            .isEqualTo("approval")
        assertThat(json.decodeFromString<NotificationTokenComponent.Offer>("{}").reason).isNull()
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

    @Test
    fun `sidebar's connect names the sidebar page and what the page on screen belongs under, both optional`() {
        val connect = json.decodeFromString<com.chatwithwork.app.sidebar.SidebarPage.Connect>(
            """{"page":"/482139075/sidebar","belongsTo":"/482139075/bots/4","metadata":{"url":"x"}}"""
        )
        assertThat(connect.page).isEqualTo("/482139075/sidebar")
        assertThat(connect.belongsTo).isEqualTo("/482139075/bots/4")

        val older = json.decodeFromString<com.chatwithwork.app.sidebar.SidebarPage.Connect>("""{}""")
        assertThat(older.page).isNull()
        assertThat(older.belongsTo).isNull()
    }

    @Test
    fun `web composer's state says whether an answer runs, which, and what the composer takes`() {
        val state = json.decodeFromString<WebComposerState>(
            """{"working":true,"waiting":false,"run":"run_7","revision":3,"startedAt":1763600000,"mark":"bot",
                "csrfToken":"t","acceptsFiles":true,"dictates":true,"menu":true,"metadata":{"url":"x"}}"""
        )
        assertThat(state.working).isTrue()
        assertThat(state.liveRun).isEqualTo(com.chatwithwork.app.push.LiveRunRef("run_7", 1763600000000, 3))
        assertThat(state.mark).isEqualTo("bot")
        assertThat(state.csrfToken).isEqualTo("t")
        assertThat(state.acceptsFiles).isTrue()
        assertThat(state.dictates).isTrue()
        assertThat(state.menu).isTrue()

        // Nothing running, and an older page's keys missing: nothing to follow, nothing taken.
        val idle = json.decodeFromString<WebComposerState>("""{"working":false,"run":null}""")
        assertThat(idle.liveRun).isNull()
        assertThat(idle.acceptsFiles).isFalse()
        assertThat(idle.dictates).isFalse()
        assertThat(idle.menu).isFalse()
    }

    @Test
    fun `web composer's replies hand over shared files, start dictation, and close its menus`() {
        val attach = WebComposerComponent.attachReply(
            listOf(com.chatwithwork.app.composer.ComposerFile(name = "a.pdf", type = "application/pdf", data = "QQ=="))
        )
        assertThat(attach["action"]?.jsonPrimitive?.content).isEqualTo("attach")
        val file = attach["files"]!!.jsonArray.single().jsonObject
        assertThat(file["name"]?.jsonPrimitive?.content).isEqualTo("a.pdf")
        assertThat(file["type"]?.jsonPrimitive?.content).isEqualTo("application/pdf")
        assertThat(file["data"]?.jsonPrimitive?.content).isEqualTo("QQ==")

        val dictate = WebComposerComponent.dictateReply()
        assertThat(dictate.keys).containsExactly("action")
        assertThat(dictate["action"]?.jsonPrimitive?.content).isEqualTo("dictate")

        assertThat(WebComposerComponent.closeMenusReply().toString()).isEqualTo("""{"action":"closeMenus"}""")
    }
}
