package com.chatwithwork.app.composer

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The notice above the composer (out of credits, a failed answer) sits on an
 * opaque surface like the box's, so the chat behind never shows through it.
 */
class ComposerNoticeTest {
    private val android = "http://schemas.android.com/apk/res/android"

    private fun read(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))
        .documentElement

    private fun Element.child(name: String) = getElementsByTagName(name).item(0) as Element

    @Test
    fun `the lock notice has the notice's background and room inside it`() {
        val layout = read("src/main/res/layout/view_composer.xml")
        val views = layout.getElementsByTagName("TextView")
        val lock = (0 until views.length).map { views.item(it) as Element }
            .single { it.getAttributeNS(android, "id") == "@+id/composer_lock" }
        assertThat(lock.getAttributeNS(android, "background")).isEqualTo("@drawable/composer_notice")
        assertThat(lock.getAttributeNS(android, "paddingHorizontal")).isNotEmpty()
        assertThat(lock.getAttributeNS(android, "paddingVertical")).isNotEmpty()
    }

    @Test
    fun `the notice is the box's opaque surface, a hairline and the box's corners`() {
        val shape = read("src/main/res/drawable/composer_notice.xml")
        assertThat(shape.child("solid").getAttributeNS(android, "color")).isEqualTo("@color/lw_surface")
        assertThat(shape.child("stroke").getAttributeNS(android, "width")).isEqualTo("1dp")
        assertThat(shape.child("stroke").getAttributeNS(android, "color")).isEqualTo("@color/lw_line")
        assertThat(shape.child("corners").getAttributeNS(android, "radius"))
            .isEqualTo("${ComposerBackground.RADIUS_DP.toInt()}dp")
    }

    @Test
    fun `the surface is opaque in light and dark`() {
        for (values in listOf("values", "values-night")) {
            val colors = read("src/main/res/$values/colors.xml").getElementsByTagName("color")
            val surface = (0 until colors.length).map { colors.item(it) as Element }
                .single { it.getAttribute("name") == "lw_surface" }
                .textContent.trim()
            // #RRGGBB, or #AARRGGBB with FF alpha.
            assertThat(surface.length == 7 || surface.startsWith("#FF", ignoreCase = true)).isTrue()
        }
    }
}
