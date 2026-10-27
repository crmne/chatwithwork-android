package com.chatwithwork.app.files

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileDownloadsTest {
    @Test
    fun `a shared file keeps its name, without folders`() {
        assertThat(FileDownloads.safeName("Q3 board/deck.pptx")).isEqualTo("Q3 board-deck.pptx")
        assertThat(FileDownloads.safeName("report.docx")).isEqualTo("report.docx")
        assertThat(FileDownloads.safeName("..")).isEqualTo("file")
        assertThat(FileDownloads.safeName("  ")).isEqualTo("file")
    }
}
