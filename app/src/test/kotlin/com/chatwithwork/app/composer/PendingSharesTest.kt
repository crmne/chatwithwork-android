package com.chatwithwork.app.composer

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class PendingSharesTest {
    private val plan = ComposerFile("plan.pdf", "application/pdf", "AA==")
    private val photo = ComposerFile("photo.jpg", "image/jpeg", "AQ==")
    private val notes = ComposerFile("notes.txt", "text/plain", "Ag==")
    private val page = 1
    private val other = 2

    @Before
    @After
    fun empty() {
        PendingShares.returnUnanswered()
        PendingShares.visitStarted(owner = 0)
        PendingShares.takeFiles()
    }

    private fun taken(name: String) = AttachedFile(name, true)

    private fun kept(name: String, reason: String) = AttachedFile(name, false, reason)

    @Test
    fun `files not taken wait again, ahead of newer ones, and new shares join those waiting`() {
        PendingShares.putFiles(listOf(plan))
        val taken = PendingShares.takeFiles()!!
        PendingShares.putFiles(listOf(photo))
        PendingShares.putBack(taken)
        PendingShares.putFiles(listOf(notes))

        assertThat(PendingShares.takeFiles()).containsExactly(plan, photo, notes).inOrder()
        assertThat(PendingShares.waiting).isFalse()
        assertThat(PendingShares.takeFiles()).isNull()
    }

    @Test
    fun `each reason leaves, goes again on this page, or waits for the next New chat`() {
        PendingShares.putFiles(listOf(plan, photo, notes, plan, photo, notes))
        val (id, sent) = PendingShares.sendFiles(owner = page)!!
        assertThat(sent).hasSize(6)
        assertThat(PendingShares.unanswered).isEqualTo(6)
        assertThat(PendingShares.waiting).isFalse()

        PendingShares.settle(
            id,
            listOf(
                taken("plan.pdf"),
                kept("photo.jpg", "locked"),
                kept("notes.txt", "refused"),
                kept("plan.pdf", "failed"),
                kept("photo.jpg", "unavailable"),
                kept("notes.txt", "a later reason")
            )
        )
        assertThat(PendingShares.unanswered).isEqualTo(0)
        // Once the composer unlocks, this page gets only what it may try again.
        val connected = PendingShares.visit
        assertThat(
            PendingShares.sendFiles(owner = page, connectedAt = connected)!!.second
        ).containsExactly(photo, photo)
        assertThat(PendingShares.sendFiles(owner = page, connectedAt = connected)).isNull()

        PendingShares.visitStarted(owner = other)
        assertThat(PendingShares.sendFiles(owner = other)!!.second).containsExactly(plan, notes).inOrder()
    }

    @Test
    fun `a failed file goes to the next New chat even if it connects locked and unlocks later`() {
        PendingShares.putFiles(listOf(plan))
        val (id, _) = PendingShares.sendFiles(owner = page)!!
        PendingShares.settle(id, listOf(kept("plan.pdf", "failed")))
        PendingShares.visitStarted(owner = other)
        // The next New chat connects locked, so nothing goes then; its unlock later is only a state.
        val connected = PendingShares.visit
        assertThat(PendingShares.sendFiles(owner = other, connectedAt = connected)!!.second).containsExactly(plan)
    }

    @Test
    fun `removed lets go, and the same bytes shared twice stay two files`() {
        PendingShares.putFiles(listOf(plan, plan))
        val (id, _) = PendingShares.sendFiles(owner = page)!!
        PendingShares.settle(id, listOf(kept("plan.pdf", "removed"), kept("plan.pdf", "locked")))
        assertThat(PendingShares.takeFiles()).containsExactly(plan)
    }

    @Test
    fun `a page that goes away gives back only its own files, for the next New chat`() {
        PendingShares.putFiles(listOf(plan))
        PendingShares.sendFiles(owner = page)
        PendingShares.putFiles(listOf(photo))
        PendingShares.sendFiles(owner = other)

        PendingShares.visitStarted(owner = page)
        assertThat(PendingShares.unanswered).isEqualTo(1)
        assertThat(PendingShares.sendFiles(owner = page)!!.second).containsExactly(plan)
    }

    @Test
    fun `a late answer counts wherever the file is now, so a file the page took never comes back`() {
        PendingShares.putFiles(listOf(plan, photo))
        val (first, _) = PendingShares.sendFiles(owner = page, now = 0)!!
        // Ten minutes without a word: the files wait for the next New chat, which takes them again.
        PendingShares.returnUnanswered(olderThan = PendingShares.UNANSWERED_MS)
        PendingShares.visitStarted(owner = page)
        val (second, again) = PendingShares.sendFiles(owner = other)!!
        assertThat(again).containsExactly(plan, photo).inOrder()

        PendingShares.settle(first, listOf(taken("plan.pdf"), kept("photo.jpg", "unavailable")))
        assertThat(PendingShares.unanswered).isEqualTo(1)
        PendingShares.settle(second, listOf(kept("plan.pdf", "failed"), kept("photo.jpg", "failed")))

        PendingShares.visitStarted(owner = other)
        assertThat(PendingShares.takeFiles()).containsExactly(photo)
    }

    @Test
    fun `a file that failed before, then comes back locked, goes again on this page once it unlocks`() {
        PendingShares.putFiles(listOf(notes))
        val (first, _) = PendingShares.sendFiles(owner = page)!!
        PendingShares.settle(first, listOf(kept("notes.txt", "failed")))
        PendingShares.visitStarted(owner = other)
        val (second, _) = PendingShares.sendFiles(owner = page)!!
        PendingShares.settle(second, listOf(kept("notes.txt", "locked")))
        assertThat(PendingShares.sendFiles(owner = page)!!.second).containsExactly(notes)
    }

    @Test
    fun `an attach the reply couldn't carry waits again at once`() {
        PendingShares.putFiles(listOf(notes))
        val (id, _) = PendingShares.sendFiles(owner = page)!!
        PendingShares.unsend(id)
        assertThat(PendingShares.unanswered).isEqualTo(0)
        assertThat(PendingShares.sendFiles(owner = page)!!.second).containsExactly(notes)
    }
}
