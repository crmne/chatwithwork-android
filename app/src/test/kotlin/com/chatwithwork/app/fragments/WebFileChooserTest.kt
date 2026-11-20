package com.chatwithwork.app.fragments

import com.chatwithwork.app.fragments.WebFileChooser.Source
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebFileChooserTest {
    @Test
    fun `an input that takes any file offers every source`() {
        assertThat(WebFileChooser.sources(emptyList())).isEqualTo(Source.entries)
        assertThat(WebFileChooser.sources(listOf(""))).isEqualTo(Source.entries)
        assertThat(WebFileChooser.sources(listOf("*/*"))).isEqualTo(Source.entries)
    }

    @Test
    fun `an input for pictures offers the camera and photos`() {
        assertThat(WebFileChooser.sources(listOf("image/*"))).containsExactly(Source.CAMERA, Source.PHOTOS).inOrder()
        assertThat(
            WebFileChooser.sources(listOf("image/png", "image/jpeg"))
        ).containsExactly(Source.CAMERA, Source.PHOTOS)
    }

    @Test
    fun `anything narrower is left to Hotwire Native's chooser`() {
        assertThat(WebFileChooser.sources(listOf(".csv"))).isNull()
        assertThat(WebFileChooser.sources(listOf("application/pdf", "image/png"))).isNull()
    }
}
