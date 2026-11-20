package com.chatwithwork.app.fragments

import com.chatwithwork.app.fragments.WebFileChooser.Source
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebFileChooserTest {
    @Test
    fun `an input that takes several files of any kind offers every source`() {
        assertThat(WebFileChooser.sources(emptyList(), multiple = true)).isEqualTo(Source.entries)
        assertThat(WebFileChooser.sources(listOf(""), multiple = true)).isEqualTo(Source.entries)
        assertThat(WebFileChooser.sources(listOf("*/*"), multiple = true)).isEqualTo(Source.entries)
    }

    @Test
    fun `anything narrower is left to Hotwire Native's chooser`() {
        // The photo picker also offers videos, and the camera makes JPEGs.
        assertThat(WebFileChooser.sources(listOf("image/*"), multiple = true)).isNull()
        assertThat(WebFileChooser.sources(listOf(".csv"), multiple = true)).isNull()
        // The pickers let several be chosen.
        assertThat(WebFileChooser.sources(emptyList(), multiple = false)).isNull()
    }
}
