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
        {"placeholder":"Ask anything…","locked":false,"lockReason":null,"working":false,"stopping":false,
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
    fun `while an answer runs, the button stops it unless there's something to steer with`() {
        val working = ComposerState(working = true)
        assertThat(working.showsStop("")).isTrue()
        assertThat(working.showsStop("And check Slack")).isFalse()
        assertThat(ComposerState().showsStop("")).isFalse()
    }

    @Test
    fun `files go to the page as name, type, and base64`() {
        val encoded = json.encodeToString(ComposerFile.serializer(), ComposerFile("notes.txt", "text/plain", "aGk="))
        assertThat(encoded).isEqualTo("""{"name":"notes.txt","type":"text/plain","data":"aGk="}""")
        assertThat(ComposerFiles.typeFor("Scan.PDF")).isEqualTo("application/pdf")
        assertThat(ComposerFiles.typeFor("noext")).isEqualTo("application/octet-stream")
    }
}
