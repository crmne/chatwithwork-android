package com.chatwithwork.app.composer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContextWrapper
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.core.view.ContentInfoCompat
import com.chatwithwork.app.composer.PastedContent.Item
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * What a paste, the keyboard, or a drop hands the composer: files attach,
 * words stay words, by the web composer's own rule (`chat#onPaste`).
 */
@RunWith(RobolectricTestRunner::class)
class PastedContentTest {
    private val picture = Uri.parse("content://com.google.android.inputmethod.latin.fileprovider/clipboard/1.png")
    private val pdf = Uri.parse("content://com.android.providers.downloads.documents/document/42")

    @Test
    fun `words are words`() {
        assertThat(PastedContent.files(listOf(Item(text = "What did we sell in May?")))).isEmpty()
        assertThat(PastedContent.files(emptyList())).isEmpty()
    }

    @Test
    fun `a picture alone attaches`() {
        assertThat(PastedContent.files(listOf(Item(uri = picture)))).containsExactly(picture)
    }

    @Test
    fun `a copied file attaches, its name beside it notwithstanding`() {
        val items = listOf(Item(text = "Q3 report.pdf", uri = pdf))
        assertThat(PastedContent.files(items)).containsExactly(pdf)
    }

    @Test
    fun `an image copied from a page attaches, its HTML only the picture`() {
        val items = listOf(Item(text = " ", html = "<img src=\"https://example.com/chart.png\">", uri = picture))
        assertThat(PastedContent.files(items)).containsExactly(picture)
    }

    @Test
    fun `a spreadsheet range copied with a picture of it is its words`() {
        val items =
            listOf(
                Item(
                    text = "Region\tSales\nNorth\t120",
                    html = "<table><tr><td>Region</td><td>Sales</td></tr></table>"
                ),
                Item(uri = picture)
            )
        assertThat(PastedContent.files(items)).isEmpty()
    }

    @Test
    fun `a web link is words, not a file`() {
        val link = Uri.parse("https://example.com/chart.png")
        assertThat(PastedContent.files(listOf(Item(text = link.toString(), uri = link)))).isEmpty()
        assertThat(PastedContent.files(listOf(Item(uri = link)))).isEmpty()
    }

    @Test
    fun `HTML has words only where it says something`() {
        assertThat(PastedContent.hasWords("<meta charset=\"utf-8\"><img src=\"a.png\">")).isFalse()
        assertThat(PastedContent.hasWords("<style>td { color: red }</style><img src=\"a.png\">&nbsp;")).isFalse()
        assertThat(PastedContent.hasWords("<!-- StartFragment --><b>North</b>")).isTrue()
        assertThat(PastedContent.hasWords(null)).isFalse()
    }

    private class Host(base: android.content.Context) :
        ContextWrapper(base),
        PastedContent.Host {
        val received = mutableListOf<Uri>()
        var released = 0

        override fun composerTakesFiles() = true

        override fun receivePasted(uris: List<Uri>, release: () -> Unit) {
            received += uris
            release()
            released++
        }
    }

    private fun receive(
        clip: ClipData,
        source: Int = ContentInfoCompat.SOURCE_INPUT_METHOD
    ): Pair<Host, ContentInfoCompat?> {
        val host = Host(RuntimeEnvironment.getApplication())
        val view = View(host)
        val left = PastedContent.receiver.onReceiveContent(view, ContentInfoCompat.Builder(clip, source).build())
        shadowOf(Looper.getMainLooper()).idle()
        return host to left
    }

    @Test
    fun `the keyboard's picture goes to the host, which releases its grant once read`() {
        val (host, left) = receive(ClipData(ClipDescription("sticker", arrayOf("image/webp")), ClipData.Item(picture)))
        assertThat(left).isNull()
        assertThat(host.received).containsExactly(picture)
        assertThat(host.released).isEqualTo(1)
    }

    @Test
    fun `words are left to the page`() {
        val (host, left) = receive(ClipData.newPlainText("", "Hello"), ContentInfoCompat.SOURCE_CLIPBOARD)
        assertThat(left).isNotNull()
        assertThat(host.received).isEmpty()
    }

    @Test
    fun `the host is the activity behind the web view's context`() {
        val host = Host(RuntimeEnvironment.getApplication())
        assertThat(PastedContent.hostOf(ContextWrapper(host))).isSameInstanceAs(host)
        assertThat(PastedContent.hostOf(RuntimeEnvironment.getApplication())).isNull()
    }
}
