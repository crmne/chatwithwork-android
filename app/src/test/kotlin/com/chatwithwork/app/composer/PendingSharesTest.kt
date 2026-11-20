package com.chatwithwork.app.composer

import com.chatwithwork.app.bridge.AttachedMessage
import com.chatwithwork.app.bridge.WebComposerComponent
import com.chatwithwork.app.bridge.WebComposerState
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test

class PendingSharesTest {
    private val plan = ComposerFile("plan.pdf", "application/pdf", "AA==")
    private val photo = ComposerFile("photo.jpg", "image/jpeg", "AQ==")
    private val notes = ComposerFile("notes.txt", "text/plain", "Ag==")

    @After
    fun empty() {
        PendingShares.returnUnanswered()
        PendingShares.takeFiles()
    }

    @Test
    fun `files a composer couldn't take wait again, ahead of any shared since`() {
        PendingShares.putFiles(listOf(plan))
        val taken = PendingShares.takeFiles()!!
        PendingShares.putFiles(listOf(photo))
        PendingShares.putBack(taken)

        assertThat(PendingShares.takeFiles()).containsExactly(plan, photo).inOrder()
        assertThat(PendingShares.waiting).isFalse()
    }

    @Test
    fun `nothing put back leaves nothing waiting`() {
        PendingShares.putBack(emptyList())
        assertThat(PendingShares.takeFiles()).isNull()
    }

    @Test
    fun `files in flight leave only as the page answers, by reason`() {
        PendingShares.putFiles(listOf(plan, photo, notes))
        val (id, sent) = PendingShares.sendFiles()!!
        assertThat(sent).containsExactly(plan, photo, notes).inOrder()
        assertThat(PendingShares.takeFiles()).isNull()
        assertThat(PendingShares.unanswered).isEqualTo(3)

        PendingShares.settle(
            id,
            listOf(
                AttachedFile("plan.pdf", true),
                AttachedFile("photo.jpg", false, "locked"),
                AttachedFile("notes.txt", false, "refused")
            )
        )

        assertThat(PendingShares.unanswered).isEqualTo(0)
        assertThat(PendingShares.takeFiles()).containsExactly(photo)
    }

    @Test
    fun `failed, unavailable and reasons not known yet keep the file, removed lets it go`() {
        PendingShares.putFiles(listOf(plan, photo, notes, plan))
        val (id, _) = PendingShares.sendFiles()!!
        PendingShares.settle(
            id,
            listOf(
                AttachedFile("plan.pdf", false, "failed"),
                AttachedFile("photo.jpg", false, "unavailable"),
                AttachedFile("notes.txt", false, "removed"),
                AttachedFile("plan.pdf", false, "a-later-reason")
            )
        )
        assertThat(PendingShares.takeFiles()).containsExactly(plan, photo, plan).inOrder()
    }

    @Test
    fun `a page gone or quiet for ten minutes gives files back, and a late answer still counts`() {
        PendingShares.putFiles(listOf(plan))
        val (old, _) = PendingShares.sendFiles(now = 0)!!
        PendingShares.putFiles(listOf(photo))
        val (recent, _) = PendingShares.sendFiles(now = PendingShares.UNANSWERED_MS)!!

        PendingShares.returnUnanswered(olderThan = PendingShares.UNANSWERED_MS - 1)
        assertThat(PendingShares.unanswered).isEqualTo(1)
        PendingShares.settle(old, listOf(AttachedFile("plan.pdf", true)))
        assertThat(PendingShares.takeFiles()).isNull()

        PendingShares.returnUnanswered()
        assertThat(PendingShares.takeFiles()).containsExactly(photo)
        PendingShares.settle(recent, listOf(AttachedFile("photo.jpg", true)))
        assertThat(PendingShares.takeFiles()).isNull()
    }

    @Test
    fun `the attach carries its id only for a page that answers, and the answer reads`() {
        assertThat(WebComposerComponent.attachReply(listOf(plan)).containsKey("id")).isFalse()
        assertThat(WebComposerComponent.attachReply(listOf(plan), "a1")["id"]!!.jsonPrimitive.content).isEqualTo("a1")

        val json = Json { ignoreUnknownKeys = true }
        assertThat(json.decodeFromString<WebComposerState>("""{"acceptsFiles":true}""").confirmsAttach).isFalse()
        assertThat(
            json.decodeFromString<WebComposerState>("""{"acceptsFiles":true,"confirmsAttach":true}""").confirmsAttach
        ).isTrue()
        val answer = json.decodeFromString<AttachedMessage>(
            """{"id":"a1","taken":false,"files":[{"name":"plan.pdf","taken":true},{"name":null,"taken":false,"reason":"locked"}],"metadata":{"url":"https://chatwithwork.com/1/chats/new"}}"""
        )
        assertThat(answer.id).isEqualTo("a1")
        assertThat(
            answer.files
        ).containsExactly(AttachedFile("plan.pdf", true), AttachedFile(null, false, "locked")).inOrder()
    }
}
