package com.chatwithwork.app.bridge

import com.chatwithwork.app.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarSymbolTest {
    @Test
    fun `has a vector for every symbol the server puts on the bar`() {
        val counts = (1..9).map { "counter_$it" } + "filter_9_plus"
        val names = listOf("right_panel_open", "add", "edit_square", "share") + counts + counts.map { "$it.fill" }

        names.forEach { assertThat(BarSymbol.vector(it)).isNotNull() }
    }

    @Test
    fun `keeps a filled count apart from its outline`() {
        assertThat(BarSymbol.vector("counter_3")).isEqualTo(R.drawable.ic_counter_3)
        assertThat(BarSymbol.vector("counter_3.fill")).isEqualTo(R.drawable.ic_counter_3_fill)
    }

    @Test
    fun `leaves other names to the font`() {
        assertThat(BarSymbol.vector("keep")).isNull()
        assertThat(BarSymbol.vector("square.and.arrow.up")).isNull()
    }
}
