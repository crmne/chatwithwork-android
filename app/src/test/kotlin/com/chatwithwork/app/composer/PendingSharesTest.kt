package com.chatwithwork.app.composer

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class PendingSharesTest {
    @After
    fun empty() {
        PendingShares.takeFiles()
    }

    @Test
    fun `files a composer couldn't take wait again, ahead of any shared since`() {
        val first = ComposerFile("plan.pdf", "application/pdf", "AA==")
        val later = ComposerFile("photo.jpg", "image/jpeg", "AQ==")

        PendingShares.putFiles(listOf(first))
        val taken = PendingShares.takeFiles()!!
        PendingShares.putFiles(listOf(later))
        PendingShares.putBack(taken)

        assertThat(PendingShares.takeFiles()).containsExactly(first, later).inOrder()
        assertThat(PendingShares.waiting).isFalse()
    }

    @Test
    fun `nothing put back leaves nothing waiting`() {
        PendingShares.putBack(emptyList())
        assertThat(PendingShares.takeFiles()).isNull()
    }
}
