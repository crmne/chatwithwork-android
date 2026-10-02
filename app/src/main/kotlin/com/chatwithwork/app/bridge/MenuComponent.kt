package com.chatwithwork.app.bridge

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import com.chatwithwork.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `menu`: actions for the page in the top app bar. Same messages as Joe
 * Masilotti's component and the iOS app's: `connect` with the items,
 * answered with the chosen `index`, and `disconnect`.
 *
 * Usually these are the page's secondary actions (pin, rename, move,
 * delete), so they go in the overflow menu (the three dots), the way
 * Android apps put them. Items may be `destructive` (red), `checked` (a check
 * mark) or `disabled`.
 *
 * A menu on the left (`side: "left"`) or with a `header` picks one thing,
 * as on iOS: it's the organization switcher. Android has no left side for
 * actions, so it becomes a button showing its `label` (the current
 * organization) that opens a sheet titled with its `header`, the chosen item
 * checked.
 */
class MenuComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_MENU

    private var data: Data? = null
    private var sheet: BottomSheetDialog? = null

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                data = message.data<Data>()
                redraw()
            }

            "disconnect" -> {
                data = null
                sheet?.dismiss()
                clear()
            }
        }
    }

    override fun onStop() {
        sheet?.dismiss()
        sheet = null
    }

    override fun render(toolbar: Toolbar) {
        val data = data ?: return
        if (data.items.isEmpty()) return
        if (data.isChooser) renderChooser(toolbar, data) else renderOverflow(toolbar, data.items)
    }

    private fun renderOverflow(toolbar: Toolbar, items: List<Item>) {
        val error = MaterialColors.getColor(toolbar, AppCompatR.attr.colorError)

        items.forEachIndexed { index, item ->
            val title =
                if (item.destructive == true) {
                    SpannableString(item.title).apply {
                        setSpan(ForegroundColorSpan(error), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    item.title
                }

            toolbar.menu.add(menuGroup, ITEM_BASE + index, ORDER_MENU + index, title).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                isEnabled = item.disabled != true
                // The web sends checked: false for every item; only a checked one shows a mark.
                if (item.checked == true) {
                    isCheckable = true
                    isChecked = true
                }
                setOnMenuItemClickListener {
                    choose(index)
                    true
                }
            }
        }
    }

    private fun renderChooser(toolbar: Toolbar, data: Data) {
        val context = toolbar.context
        val title = data.label ?: data.header ?: context.getString(R.string.menu)
        val description = listOfNotNull(data.header, data.label).joinToString(": ").ifEmpty { title }
        val button =
            (LayoutInflater.from(context).inflate(R.layout.toolbar_chooser, toolbar, false) as MaterialButton).apply {
                text = title
                contentDescription = description
                TooltipCompat.setTooltipText(this, description)
                setOnClickListener { showSheet(data) }
            }
        // Before the page's other actions: on iOS it sits on the opposite side.
        toolbar.menu.add(menuGroup, ITEM_BASE, ORDER_CHOOSER, title).apply {
            actionView = button
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            describe(description)
        }
    }

    private fun showSheet(data: Data) {
        val context = destination.fragment.context ?: return
        sheet?.dismiss()
        val actions =
            data.items.map {
                ActionSheet.Action(
                    title = it.title,
                    image = it.image,
                    destructive = it.destructive == true,
                    disabled = it.disabled == true,
                    checked = it.checked == true
                )
            }
        val dialog = ActionSheet.show(context, data.header, actions) { index -> choose(index) }
        dialog.setOnDismissListener { if (sheet === dialog) sheet = null }
        sheet = dialog
    }

    private fun choose(index: Int) {
        replyTo("connect", Selection(index))
    }

    @Serializable
    data class Data(
        val items: List<Item> = emptyList(),
        val color: String? = null,
        val side: String? = null,
        val label: String? = null,
        val header: String? = null
    ) {
        /** A menu for picking one thing (on the left, or with a header), not a page's actions. */
        val isChooser: Boolean
            get() = side == "left" || header != null
    }

    @Serializable
    data class Item(
        val title: String,
        @SerialName("androidImage") val image: String? = null,
        val destructive: Boolean? = null,
        val checked: Boolean? = null,
        val disabled: Boolean? = null,
        /** A stable name for the action, for native code that needs one; never the title. */
        val nativeAction: String? = null
    )

    @Serializable
    data class Selection(val index: Int)

    companion object {
        private const val ITEM_BASE = 0x0C500
        private const val ORDER_CHOOSER = 5

        val factory = BridgeComponentFactory("menu", ::MenuComponent)
    }
}
