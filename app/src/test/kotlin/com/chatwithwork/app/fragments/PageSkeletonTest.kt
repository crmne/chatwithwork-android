package com.chatwithwork.app.fragments

import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.fragments.PageSkeleton.Kind
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageSkeletonTest {
    private val base = AppConfig.baseUrl

    // A 411dp phone at density 2.625, its bar's bottom at 80dp, its navigation bar 48dp tall.
    private val density = 2.625f
    private val width = 1080f
    private val height = 2200f
    private val top = 210f
    private val bottom = height - 48 * density
    private val margin = 16 * density
    private val column = width - 2 * margin

    private fun sketch(
        kind: Kind,
        width: Float = this.width,
        height: Float = this.height,
        bottom: Float = this.bottom,
        rtl: Boolean = false
    ) = PageSkeleton.sketch(kind, width, height, top, bottom, density, rtl)

    private fun dp(value: Float) = value * density

    @Test
    fun `the server's skeleton property picks the kind`() {
        val page = "$base/482139075/bots/7"
        assertThat(PageSkeleton.kind(page, "chat")).isEqualTo(Kind.CHAT)
        assertThat(PageSkeleton.kind(page, "new_chat")).isEqualTo(Kind.NEW_CHAT)
        assertThat(PageSkeleton.kind(page, "list")).isEqualTo(Kind.LIST)
        assertThat(PageSkeleton.kind(page, "settings")).isEqualTo(Kind.SETTINGS)
        assertThat(PageSkeleton.kind(page, "form")).isEqualTo(Kind.FORM)
        assertThat(PageSkeleton.kind(page, "page")).isEqualTo(Kind.PAGE)
        assertThat(PageSkeleton.kind(page, "none")).isNull()
    }

    @Test
    fun `an unknown kind draws a page`() {
        assertThat(PageSkeleton.kind("$base/482139075/bots/7", "calendar")).isEqualTo(Kind.PAGE)
        assertThat(PageSkeleton.kind("$base/482139075/bots/7", "LIST")).isEqualTo(Kind.PAGE)
    }

    @Test
    fun `a server that doesn't say keeps a chat's skeleton for chats and none elsewhere`() {
        assertThat(PageSkeleton.kind("$base/482139075/chats/42", null)).isEqualTo(Kind.CHAT)
        assertThat(PageSkeleton.kind("$base/482139075/chats/42?from=decisions", null)).isEqualTo(Kind.CHAT)
        assertThat(PageSkeleton.kind("$base/482139075/chats/new", null)).isNull()
        assertThat(PageSkeleton.kind("$base/482139075/decisions", null)).isNull()
        assertThat(PageSkeleton.kind("$base/482139075/bots/7", null)).isNull()
    }

    @Test
    fun `a chat's sketch is the chat skeleton, its composer apart`() {
        val chat = ChatSkeleton.body(width, height, top, density, rtl = false)
        val sketch = sketch(Kind.CHAT)

        assertThat(sketch.bones).isEqualTo(chat.dropLast(1))
        assertThat(sketch.composer).isEqualTo(chat.last())
        assertThat(sketch.boxes).isEmpty()
    }

    @Test
    fun `New chat greets above three suggestions over the composer at rest`() {
        val sketch = sketch(Kind.NEW_CHAT)
        val (greeting, first, second, third) = sketch.bones
        val composer = sketch.composer!!

        assertThat(sketch.bones).hasSize(4)
        assertThat(composer).isEqualTo(ChatSkeleton.body(width, height, top, density, rtl = false).last())
        assertThat((greeting.top + greeting.bottom) / 2).isWithin(0.01f).of(top + (composer.top - top) * 0.4f)
        assertThat(greeting.bottom - greeting.top).isWithin(0.01f).of(dp(22f))
        assertThat(greeting.right - greeting.left).isWithin(0.01f).of(column * 0.6f)
        assertThat((greeting.left + greeting.right) / 2).isWithin(0.01f).of(width / 2)
        assertThat(first.top - greeting.bottom).isWithin(0.01f).of(dp(20f))
        assertThat(second.top - first.bottom).isWithin(0.01f).of(dp(8f))
        listOf(first to 0.44f, second to 0.58f, third to 0.36f).forEach { (pill, share) ->
            assertThat(pill.bottom - pill.top).isWithin(0.01f).of(dp(32f))
            assertThat(pill.right - pill.left).isWithin(0.01f).of(column * share)
            assertThat(pill.radii).containsExactly(dp(16f), dp(16f), dp(16f), dp(16f))
            assertThat((pill.left + pill.right) / 2).isWithin(0.01f).of(width / 2)
        }
    }

    @Test
    fun `a list is rows of a mark and two lines, as many as fit`() {
        val sketch = sketch(Kind.LIST)
        val rows = ((bottom - top - dp(16f)) / dp(64f)).toInt()
        val (mark, firstLine, secondLine) = sketch.bones
        val textStart = margin + dp(32f) + dp(12f)
        val text = width - margin - textStart

        assertThat(sketch.bones).hasSize(rows * 3)
        assertThat(sketch.composer).isNull()
        assertThat(mark.left).isEqualTo(margin)
        assertThat(mark.right - mark.left).isWithin(0.01f).of(dp(32f))
        assertThat((mark.top + mark.bottom) / 2).isWithin(0.01f).of(top + dp(16f) + dp(32f))
        assertThat(mark.radii.first()).isWithin(0.01f).of(dp(16f))
        assertThat(firstLine.left).isWithin(0.01f).of(textStart)
        assertThat(firstLine.top).isWithin(0.01f).of(top + dp(16f) + dp(16f))
        assertThat(firstLine.bottom - firstLine.top).isWithin(0.01f).of(dp(12f))
        assertThat(firstLine.right - firstLine.left).isWithin(0.01f).of(text * 0.56f)
        assertThat(secondLine.top).isWithin(0.01f).of(top + dp(16f) + dp(38f))
        assertThat(secondLine.bottom - secondLine.top).isWithin(0.01f).of(dp(10f))
        assertThat(secondLine.right - secondLine.left).isWithin(0.01f).of(text * 0.34f)
        // The second row's lines take the next widths, and the fifth's start over.
        assertThat(sketch.bones[4].right - sketch.bones[4].left).isWithin(0.01f).of(text * 0.42f)
        assertThat(sketch.bones[13].right - sketch.bones[13].left).isWithin(0.01f).of(text * 0.56f)
        assertThat(sketch.bones.last().bottom).isAtMost(bottom)
    }

    @Test
    fun `settings is two sections of four and three rows in boxes`() {
        val sketch = sketch(Kind.SETTINGS)
        val (first, second) = sketch.boxes
        val heading = sketch.bones[0]
        val muted = sketch.bones[1]
        val inner = column - dp(32f)

        assertThat(sketch.boxes).hasSize(2)
        assertThat(sketch.bones).hasSize(2 + 4 * 2 + 2 + 3 * 2)
        assertThat(heading.top).isWithin(0.01f).of(top + dp(16f))
        assertThat(heading.bottom - heading.top).isWithin(0.01f).of(dp(14f))
        assertThat(heading.right - heading.left).isWithin(0.01f).of(column * 0.36f)
        assertThat(muted.top - heading.bottom).isWithin(0.01f).of(dp(10f))
        assertThat(muted.right - muted.left).isWithin(0.01f).of(column * 0.72f)
        assertThat(first.top - muted.bottom).isWithin(0.01f).of(dp(14f))
        assertThat(first.left).isEqualTo(margin)
        assertThat(first.right).isEqualTo(width - margin)
        assertThat(first.radius).isWithin(0.01f).of(dp(14f))
        assertThat(first.bottom - first.top).isWithin(0.01f).of(dp(52f) * 4)
        assertThat(first.dividers).hasSize(3)
        assertThat(first.dividers.first()).isWithin(0.01f).of(first.top + dp(52f))
        assertThat(second.bottom - second.top).isWithin(0.01f).of(dp(52f) * 3)
        assertThat(sketch.bones[10].top - first.bottom).isWithin(0.01f).of(dp(28f))

        val label = sketch.bones[2]
        val value = sketch.bones[3]
        assertThat(label.left).isWithin(0.01f).of(first.left + dp(16f))
        assertThat((label.top + label.bottom) / 2).isWithin(0.01f).of(first.top + dp(26f))
        assertThat(label.right - label.left).isWithin(0.01f).of(inner * 0.44f)
        assertThat(sketch.bones[4].right - sketch.bones[4].left).isWithin(0.01f).of(inner * 0.56f)
        assertThat(value.right).isWithin(0.01f).of(first.right - dp(16f))
        assertThat(value.right - value.left).isWithin(0.01f).of(inner * 0.14f)
    }

    @Test
    fun `a form is four labelled fields`() {
        val sketch = sketch(Kind.FORM)
        val label = sketch.bones.first()
        val field = sketch.boxes.first()

        assertThat(sketch.bones).hasSize(4)
        assertThat(sketch.boxes).hasSize(4)
        assertThat(label.top).isWithin(0.01f).of(top + dp(16f))
        assertThat(label.bottom - label.top).isWithin(0.01f).of(dp(10f))
        assertThat(sketch.bones.map { (it.right - it.left) / column })
            .comparingElementsUsing(Correspondence.tolerance(0.001))
            .containsExactly(0.3, 0.22, 0.36, 0.26)
            .inOrder()
        assertThat(field.top - label.bottom).isWithin(0.01f).of(dp(8f))
        assertThat(field.bottom - field.top).isWithin(0.01f).of(dp(44f))
        assertThat(field.right - field.left).isWithin(0.01f).of(column)
        assertThat(field.dividers).isEmpty()
        assertThat(sketch.bones[1].top - field.bottom).isWithin(0.01f).of(dp(20f))
    }

    @Test
    fun `a page is a heading, two lines of text, and a group of four rows`() {
        val sketch = sketch(Kind.PAGE)
        val (heading, line, shorter) = sketch.bones
        val box = sketch.boxes.single()

        assertThat(sketch.bones).hasSize(3 + 4 * 2)
        assertThat(heading.top).isWithin(0.01f).of(top + dp(16f))
        assertThat(heading.bottom - heading.top).isWithin(0.01f).of(dp(20f))
        assertThat(heading.right - heading.left).isWithin(0.01f).of(column * 0.52f)
        assertThat(line.right - line.left).isWithin(0.01f).of(column)
        assertThat(shorter.right - shorter.left).isWithin(0.01f).of(column * 0.68f)
        assertThat(shorter.top - line.top).isWithin(0.01f).of(dp(25.6f))
        assertThat(line.bottom - line.top).isWithin(0.01f).of(dp(12f))
        assertThat(box.top).isWithin(0.01f).of(heading.bottom + dp(12f) + 2 * dp(25.6f) + dp(24f))
        assertThat(box.bottom - box.top).isWithin(0.01f).of(dp(52f) * 4)
    }

    @Test
    fun `nothing passes the bottom of a short screen`() {
        val short = 900f
        Kind.entries.filter { it != Kind.CHAT && it != Kind.NEW_CHAT }.forEach { kind ->
            val sketch = sketch(kind, height = short, bottom = short)
            sketch.bones.forEach { assertThat(it.bottom).isAtMost(short) }
            sketch.boxes.forEach { assertThat(it.bottom).isAtMost(short) }
        }
        assertThat(sketch(Kind.SETTINGS, height = short, bottom = short).boxes.size).isLessThan(2)
        assertThat(sketch(Kind.FORM, height = short, bottom = short).boxes.size).isLessThan(4)

        val newChat = sketch(Kind.NEW_CHAT, height = 700f, bottom = 700f)
        newChat.bones.forEach { assertThat(it.bottom).isLessThan(newChat.composer!!.top) }
    }

    @Test
    fun `a tablet keeps the chat's column, centred`() {
        val wide = 3000f
        val sketch = sketch(Kind.FORM, width = wide)
        val field = sketch.boxes.first()

        assertThat(field.right - field.left).isEqualTo(768 * density)
        assertThat(field.left).isEqualTo((wide - 768 * density) / 2)
    }

    @Test
    fun `reading right to left mirrors every kind`() {
        Kind.entries.filter { it != Kind.CHAT }.forEach { kind ->
            val ltr = sketch(kind)
            val rtl = sketch(kind, rtl = true)
            ltr.bones.zip(rtl.bones).forEach { (left, right) ->
                assertThat(right.left).isWithin(0.01f).of(width - left.right)
                assertThat(right.top).isEqualTo(left.top)
            }
            ltr.boxes.zip(rtl.boxes).forEach { (left, right) ->
                assertThat(right.right).isWithin(0.01f).of(width - left.left)
            }
        }
        // A list's mark sits at the reading edge.
        assertThat(sketch(Kind.LIST, rtl = true).bones.first().right).isWithin(0.01f).of(width - margin)
    }
}
