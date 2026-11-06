package com.chatwithwork.app.fragments

import com.chatwithwork.app.AppConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatSkeletonTest {
    private val base = AppConfig.baseUrl

    // A 411dp phone at density 2.625, its bar's bottom at 80dp.
    private val density = 2.625f
    private val width = 1080f
    private val height = 2200f
    private val top = 210f

    private fun body(height: Float = this.height, width: Float = this.width, rtl: Boolean = false) =
        ChatSkeleton.body(width, height, top, density, rtl)

    @Test
    fun `chats get a skeleton, New chat and other pages don't`() {
        assertThat(ChatSkeleton.shows("$base/482139075/chats/42")).isTrue()
        assertThat(ChatSkeleton.shows("$base/482139075/chats/42?from=decisions")).isTrue()
        assertThat(ChatSkeleton.shows("$base/482139075/chats/new")).isFalse()
        assertThat(ChatSkeleton.shows("$base/482139075/decisions")).isFalse()
        assertThat(ChatSkeleton.shows("$base/482139075/chats")).isFalse()
    }

    @Test
    fun `the bones stay still with animations off`() {
        assertThat(ChatSkeleton.sweeps(animatorsEnabled = false)).isFalse()
        assertThat(ChatSkeleton.sweeps(animatorsEnabled = true)).isTrue()
    }

    @Test
    fun `two questions, seven lines of answer, and the composer`() {
        assertThat(body()).hasSize(10)
    }

    @Test
    fun `the first bubble starts below the bar and ends at the margin, its tail at the bottom end`() {
        val bubble = body().first()
        val margin = 16 * density

        assertThat(bubble.top).isGreaterThan(top)
        assertThat(bubble.right).isEqualTo(width - margin)
        assertThat(bubble.right - bubble.left).isWithin(1f).of((width - 2 * margin) * 0.62f)
        assertThat(bubble.bottom - bubble.top).isWithin(0.01f).of(44 * density)
        assertThat(bubble.radii).containsExactly(14 * density, 14 * density, 6 * density, 14 * density).inOrder()
    }

    @Test
    fun `reading right to left mirrors the bubble and its tail`() {
        val bubble = body(rtl = true).first()
        val margin = 16 * density

        assertThat(bubble.left).isEqualTo(margin)
        assertThat(bubble.radii).containsExactly(14 * density, 14 * density, 14 * density, 6 * density).inOrder()
    }

    @Test
    fun `answer lines fall on the text's rhythm, the last one shorter`() {
        val lines = body().subList(1, 5)

        assertThat(lines[1].top - lines[0].top).isWithin(0.01f).of(25.6f * density)
        assertThat(lines[0].bottom - lines[0].top).isWithin(0.01f).of(12 * density)
        assertThat(lines[3].right - lines[3].left).isWithin(1f).of((lines[0].right - lines[0].left) * 0.6f)
    }

    @Test
    fun `the composer sits where the real one rests`() {
        val composer = body().last()
        val margin = 16 * density

        assertThat(composer.left).isEqualTo(margin)
        assertThat(composer.right).isEqualTo(width - margin)
        assertThat(composer.bottom).isEqualTo(height - 10 * density)
        assertThat(composer.bottom - composer.top).isEqualTo(100 * density)
        assertThat(composer.radii).containsExactly(20 * density, 20 * density, 20 * density, 20 * density)
    }

    @Test
    fun `a short screen drops what wouldn't clear the composer`() {
        val bones = body(height = 900f)
        val composer = bones.last()

        assertThat(bones.size).isLessThan(10)
        bones.dropLast(1).forEach { assertThat(it.bottom).isLessThan(composer.top) }
    }

    @Test
    fun `a tablet keeps the chat's column, centered`() {
        val bubble = body(width = 3000f).first()
        val line = body(width = 3000f)[1]

        assertThat(line.right - line.left).isEqualTo(768 * density)
        assertThat(line.left).isEqualTo((3000f - 768 * density) / 2)
        assertThat(bubble.right).isEqualTo(line.right)
    }

    @Test
    fun `the title's bone sits at the title's start, centered on the bar`() {
        val title = ChatSkeleton.title(width, start = 200f, centerY = 150f, density = density, rtl = false)

        assertThat(title.left).isEqualTo(200f)
        assertThat(title.right - title.left).isEqualTo(120 * density)
        assertThat((title.top + title.bottom) / 2).isEqualTo(150f)
        assertThat(ChatSkeleton.title(width, 200f, 150f, density, rtl = true).right).isEqualTo(width - 200f)
    }

    @Test
    fun `the band starts and ends off screen, whichever way people read`() {
        val band = ChatSkeleton.bandWidth(width)
        val slant = height * ChatSkeleton.BAND_SLANT

        // Its right edge is furthest right at the top, its left edge furthest left at the bottom.
        assertThat(ChatSkeleton.bandOffset(0f, width, height, rtl = false) + band).isAtMost(0f)
        assertThat(ChatSkeleton.bandOffset(1f, width, height, rtl = false) - slant).isAtLeast(width)
        assertThat(ChatSkeleton.bandOffset(0f, width, height, rtl = true) - slant).isAtLeast(width)
        assertThat(ChatSkeleton.bandOffset(1f, width, height, rtl = true) + band).isAtMost(0f)
    }
}
