package com.chatwithwork.app.bridge

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SymbolDrawableTest {
    @Test
    fun `reads Material Symbols names, with an optional fill suffix`() {
        assertThat(SymbolDrawable.parse("push_pin")).isEqualTo(Symbol("push_pin", filled = false))
        assertThat(SymbolDrawable.parse("push_pin.fill")).isEqualTo(Symbol("push_pin", filled = true))
        assertThat(SymbolDrawable.parse("content_copy")).isEqualTo(Symbol("content_copy", filled = false))
    }

    @Test
    fun `refuses anything that would draw as text`() {
        assertThat(SymbolDrawable.parse(null)).isNull()
        assertThat(SymbolDrawable.parse("")).isNull()
        assertThat(SymbolDrawable.parse("square.and.arrow.up")).isNull()
        assertThat(SymbolDrawable.parse("Push Pin")).isNull()
        assertThat(SymbolDrawable.parse("a".repeat(65))).isNull()
    }
}
