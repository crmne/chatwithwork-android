package com.chatwithwork.app.composer

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** The composer's state as `bridge--composer` sends it (docs/server-contract.md, "The composer"). */
class ComposerStateTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    private val sample =
        """
        {"overlay":true,"placeholder":"Ask anything…","locked":false,"lockReason":null,"working":false,"stopping":false,
         "models":[{"name":"GPT-6 Luna","rate":"About 2 credits per answer","selected":true,"searchesWeb":true},
                   {"name":"Qwen3.8-27B","rate":null,"selected":false,"searchesWeb":false}],
         "webSearch":{"on":false,"disabled":false,"note":"Searches leave the EU."},
         "dictation":{"url":"/482139075/dictations"},"csrfToken":"t",
         "attachments":[{"id":"5","name":"Q3.pdf","size":"2.4 MB","contentType":"application/pdf","uploading":true,"progress":40}],
         "acceptsFiles":true,"somethingNew":1}
        """.trimIndent()

    @Test
    fun `reads the web composer's state`() {
        val state = json.decodeFromString<ComposerState>(sample)
        assertThat(state.selectedModel?.name).isEqualTo("GPT-6 Luna")
        assertThat(state.webSearch?.note).isEqualTo("Searches leave the EU.")
        assertThat(state.dictation?.url).isEqualTo("/482139075/dictations")
        assertThat(state.attachments.single().progress).isEqualTo(40.0)
        assertThat(state.uploading).isTrue()
        assertThat(state.overlay).isTrue()
        assertThat(json.decodeFromString<ComposerState>("{}").overlay).isFalse()
        assertThat(json.decodeFromString<ComposerState>("{}").visible).isTrue()
        assertThat(json.decodeFromString<ComposerState>("""{"visible":false}""").visible).isFalse()
    }

    @Test
    fun `permission choices are rechecked against the current page without local policy`() {
        val state = json.decodeFromString<ComposerState>(
            """{"permissionModes":{"selected":"ask","options":[
                {"value":"ask","label":"Ask for changes","selected":true,"disabled":false},
                {"value":"edits","label":"Allow edits","selected":false,"disabled":false},
                {"value":"everything","label":"Allow everything","selected":false,"disabled":true}]}}"""
        )
        assertThat(state.canSelectPermissionMode("edits")).isTrue()
        assertThat(state.canSelectPermissionMode("everything")).isFalse()
        assertThat(state.canSelectPermissionMode("unknown")).isFalse()
        assertThat(state.copy(locked = true).canSelectPermissionMode("edits")).isFalse()
        assertThat(state.copy(permissionModes = null).canSelectPermissionMode("edits")).isFalse()
        assertThat(json.decodeFromString<ComposerState>("{}").permissionModes).isNull()
    }

    @Test
    fun `model maker artwork is optional for older servers`() {
        val state = json.decodeFromString<ComposerState>(
            """{"models":[{"name":"GPT","selected":true,"iconName":"openai","invertIcon":true,"icon":"https://example.com/openai.svg"}]}"""
        )
        assertThat(state.selectedModel?.iconName).isEqualTo("openai")
        assertThat(state.selectedModel?.invertIcon).isTrue()
        assertThat(json.decodeFromString<ComposerState>(sample).selectedModel?.iconName).isNull()
    }

    @Test
    fun `sends words or files, never while a file uploads or the composer is locked`() {
        val state = ComposerState()
        assertThat(state.canSend("")).isFalse()
        assertThat(state.canSend("  ")).isFalse()
        assertThat(state.canSend("Hello")).isTrue()
        assertThat(state.copy(locked = true).canSend("Hello")).isFalse()

        val file = ComposerState.Attachment(id = "1")
        assertThat(state.copy(attachments = listOf(file)).canSend("")).isTrue()
        assertThat(state.copy(attachments = listOf(file.copy(uploading = true))).canSend("Hello")).isFalse()
    }

    @Test
    fun `while an answer runs, Stop stands alone until there's something to steer with, then Send joins it`() {
        val working = ComposerState(working = true)
        assertThat(working.showsStop()).isTrue()
        assertThat(working.showsSend("")).isFalse()
        assertThat(working.showsSend("  ")).isFalse()

        assertThat(working.showsSend("And check Slack")).isTrue()
        assertThat(working.canSend("And check Slack")).isTrue()
        assertThat(working.showsStop()).isTrue()

        val file = ComposerState.Attachment(id = "1")
        assertThat(working.copy(attachments = listOf(file)).showsSend("")).isTrue()
    }

    @Test
    fun `without an answer running, Send stands alone`() {
        val idle = ComposerState()
        assertThat(idle.showsStop()).isFalse()
        assertThat(idle.showsSend("")).isTrue()
        assertThat(idle.showsSend("Hello")).isTrue()
        assertThat(idle.canStop).isFalse()
    }

    @Test
    fun `Stop is disabled while the answer is stopping, with or without words`() {
        val stopping = ComposerState(working = true, stopping = true)
        assertThat(stopping.showsStop()).isTrue()
        assertThat(stopping.canStop).isFalse()
        assertThat(stopping.showsSend("And check Slack")).isTrue()
        assertThat(ComposerState(working = true).canStop).isTrue()
    }

    @Test
    fun `files go to the page as name, type, and base64`() {
        val encoded = json.encodeToString(ComposerFile.serializer(), ComposerFile("notes.txt", "text/plain", "aGk="))
        assertThat(encoded).isEqualTo("""{"name":"notes.txt","type":"text/plain","data":"aGk="}""")
        assertThat(ComposerFiles.typeFor("Scan.PDF")).isEqualTo("application/pdf")
        assertThat(ComposerFiles.typeFor("noext")).isEqualTo("application/octet-stream")
    }
}
