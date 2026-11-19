package com.chatwithwork.app

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chatwithwork.app.composer.AttachSheet
import com.chatwithwork.app.composer.AttachTiles
import com.chatwithwork.app.composer.ComposerBar
import com.chatwithwork.app.composer.ComposerState
import com.chatwithwork.app.composer.ComposerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The native pieces at the system's largest text (font size 200%) on a phone
 * at its largest display size (320dp wide): nothing clips, the model's name
 * stays readable, and the controls TalkBack reads have names and roles. At the
 * standard size they stay as they always were.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h693dp-xxhdpi")
class LargeTextTest {
    private val state =
        ComposerState(
            models = listOf(
                ComposerState.Model(name = "GPT-6 Luna", iconName = "openai"),
                ComposerState.Model(name = "GPT-6.1 Terra", selected = true, iconName = "openai")
            ),
            permissionModes = ComposerState.PermissionModes(
                selected = "edits",
                options = listOf(
                    ComposerState.PermissionOption("ask", "Ask first", "You approve each change"),
                    ComposerState.PermissionOption(
                        "edits",
                        "Allow edits",
                        "Deleting or overwriting still asks",
                        selected = true
                    ),
                    ComposerState.PermissionOption("everything", "Allow everything", "Off for your organization")
                )
            ),
            webSearch = ComposerState.WebSearch(on = false, note = "Searches leave the EU."),
            dictation = ComposerState.Dictation("/482139075/dictations"),
            attachments = listOf(ComposerState.Attachment(id = "1", name = "Q3 plan.pdf", size = "2.4 MB"))
        )

    private fun activity(fontScale: Float): AppCompatActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        activity.setTheme(R.style.Theme_ChatWithWork)
        return activity
    }

    private fun composer(fontScale: Float): ComposerView {
        val activity = activity(fontScale)
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val composer = LayoutInflater.from(activity).inflate(R.layout.view_composer, root, false) as ComposerView
        root.addView(composer)
        composer.show(state)
        layOut(root)
        // The bar settles once it knows its width, as on a phone: measure again with what it chose.
        layOut(root)
        return composer
    }

    private fun layOut(view: View) {
        val metrics = view.resources.displayMetrics
        view.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** How many of [view]'s characters show, the rest given to an ellipsis. */
    private fun shownCharacters(view: TextView): Int {
        val layout = view.layout ?: return view.text.length
        return view.text.length - (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
    }

    /** No child is measured taller than [parent] holds for it. */
    private fun assertNothingClips(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.GONE) continue
            assertThat(child.bottom).isAtMost(parent.height - parent.paddingBottom + 1)
            if (child is TextView && child.layout != null) {
                // Every line the text needs fits in the view's own height.
                assertThat(
                    child.layout.height
                ).isAtMost(child.height - child.compoundPaddingTop - child.compoundPaddingBottom + 1)
            }
            if (child is ViewGroup) assertNothingClips(child)
        }
    }

    @Test
    fun `at 200 percent the model's name moves to its own row and reads whole`() {
        val composer = composer(2f)
        val model = composer.findViewById<TextView>(R.id.composer_model)
        assertThat((model.parent as View).id).isEqualTo(R.id.composer_model_row)
        assertThat(model.text.toString()).isEqualTo("GPT-6.1 Terra")
        assertThat(shownCharacters(model)).isAtLeast(6)
        assertNothingClips(composer)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `at the standard size the model stays in the bar`() {
        val composer = composer(1f)
        val model = composer.findViewById<TextView>(R.id.composer_model)
        assertThat((model.parent as View).id).isEqualTo(R.id.composer_bar)
        assertThat(composer.findViewById<View>(R.id.composer_model_row).visibility).isEqualTo(View.GONE)
        // The pill and the chip keep their 44dp targets.
        val density = composer.resources.displayMetrics.density
        assertThat(model.height).isEqualTo((44 * density).toInt())
        assertThat(composer.findViewById<View>(R.id.composer_mode).height).isEqualTo((44 * density).toInt())
    }

    @Test
    fun `at 200 percent the file chip grows to hold its name and size`() {
        val composer = composer(2f)
        val chip = composer.findViewById<ViewGroup>(R.id.composer_files).getChildAt(0) as ViewGroup
        assertThat(chip.height).isGreaterThan((48 * chip.resources.displayMetrics.density).toInt())
        assertNothingClips(chip)
    }

    @Test
    fun `the model and the mode are announced as buttons, and the bar's targets are 48dp`() {
        val composer = composer(1f)
        listOf(R.id.composer_model, R.id.composer_mode).forEach { id ->
            val info = composer.findViewById<View>(id).createAccessibilityNodeInfo()
            assertThat(info.className.toString()).isEqualTo(Button::class.java.name)
        }
        val box = composer.findViewById<ViewGroup>(R.id.composer_box)
        val targets = box.touchDelegate as com.chatwithwork.app.main.TouchTargets
        val plus = composer.findViewById<View>(R.id.composer_plus)
        val min = (48 * composer.resources.displayMetrics.density).toInt()
        val area = targets.area(plus)
        assertThat(area.width()).isAtLeast(min)
        assertThat(area.height()).isAtLeast(min)
    }

    private fun sheet(fontScale: Float): View {
        val activity = activity(fontScale)
        val sheet = ComposerBar.Sheet(
            attach = true,
            folded = setOf(ComposerBar.Control.DICTATE, ComposerBar.Control.WEB_SEARCH),
            permissions = true
        )
        val dialog = AttachSheet.show(activity, state, sheet, NoChoices)
        shadowOf(Looper.getMainLooper()).idle()
        val content = dialog.findViewById<View>(R.id.attach_tiles)!!.rootView
        layOut(content)
        return content
    }

    @Test
    fun `at 200 percent every tile in the + sheet reads whole, on more rows`() {
        val content = sheet(2f)
        val tiles = content.findViewById<AttachTiles>(R.id.attach_tiles)
        assertThat(tiles.columns).isLessThan(5)
        for (i in 0 until tiles.childCount) {
            val label = tiles.getChildAt(i).findViewById<TextView>(R.id.attach_tile_label)
            assertThat(label.paint.measureText(label.text.toString())).isAtMost(label.width.toFloat() + 1)
        }
        assertNothingClips(content.findViewById(R.id.attach_permissions))
        assertNothingClips(content.findViewById(R.id.attach_web_search))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `at the standard size the + sheet's tiles share one row`() {
        val tiles = sheet(1f).findViewById<AttachTiles>(R.id.attach_tiles)
        assertThat(tiles.columns).isEqualTo(5)
    }

    @Test
    fun `the + sheet's web search switch says what it switches`() {
        val switch = sheet(1f).findViewById<MaterialSwitch>(R.id.attach_web_search_switch)
        assertThat(switch.contentDescription.toString()).isEqualTo("Search the web")
    }

    @Test
    fun `at 200 percent the fallback sidebar's switch, badge and New bot grow with their text`() {
        val activity = activity(2f)
        val inflater = LayoutInflater.from(activity)
        val parent = LinearLayout(activity)
        listOf(R.layout.sidebar_views, R.layout.sidebar_decisions, R.layout.sidebar_bots_empty, R.layout.sidebar_bot)
            .forEach { layout ->
                val view = inflater.inflate(layout, parent, false) as ViewGroup
                view.findViewById<TextView>(R.id.sidebar_decisions_count)?.apply {
                    text = "3"
                    visibility = View.VISIBLE
                }
                view.findViewById<TextView>(R.id.sidebar_bot_count)?.apply {
                    text = "2"
                    visibility = View.VISIBLE
                }
                view.findViewById<TextView>(R.id.sidebar_bot_name)?.text = "Morning briefing"
                view.findViewById<TextView>(R.id.sidebar_bot_line)?.text = "Sent the brief"
                layOut(view)
                assertNothingClips(view)
            }
    }

    private companion object {
        // A Pixel 9 at the standard display size.
        const val PHONE = "w411dp-h914dp-xxhdpi"
    }

    private object NoChoices : AttachSheet.Choices {
        override fun camera() = Unit

        override fun photos() = Unit

        override fun files() = Unit

        override fun scan() = Unit

        override fun dictate() = Unit

        override fun webSearch(on: Boolean) = Unit

        override fun narrow() = Unit

        override fun permissionMode(value: String) = Unit
    }
}
