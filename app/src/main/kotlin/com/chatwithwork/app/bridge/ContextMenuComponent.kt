package com.chatwithwork.app.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import com.chatwithwork.app.R
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `context-menu`: a native menu for one thing in the page, like the actions
 * on an answer (copy, retry, branch, share) behind one "more" button. Same
 * contract as the iOS app's component:
 *
 * - Web to native: `show` with `{items: [{title, iosImage?, androidImage?,
 *   destructive?, disabled?, copy?, copyHtml?}], rect: {x, y, width, height},
 *   scroll?: {x, y}, title?}`. `rect` is the anchor's
 *   `getBoundingClientRect()` in CSS pixels; `scroll` is for iOS and ignored
 *   here.
 * - Native to web: a reply to `show` with `{index}` when an item is chosen;
 *   nothing when the menu is dismissed.
 *
 * An item with `copy` text is copied here, with Android's clipboard, and gets
 * no reply: a page can't write the clipboard from a callback that no tap of
 * its own started. With `copyHtml` too (an answer rendered as HTML), the clip
 * carries both, so rich editors paste the formatting and plain ones the
 * Markdown.
 *
 * With a rect, it's a Material popup menu at the element, the way Android
 * shows an item's overflow. Without one (a long press has no button to
 * anchor to), it's a bottom sheet.
 */
class ContextMenuComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private var popup: PopupMenu? = null
    private var sheet: BottomSheetDialog? = null
    private var anchor: View? = null

    override fun onReceive(message: Message) {
        if (message.event != "show") return
        val data = message.data<Data>() ?: return
        if (data.items.isEmpty()) return
        dismiss()

        val rect = data.rect
        if (rect != null && showPopup(data, rect)) return
        showSheet(data)
    }

    override fun onStop() {
        dismiss()
    }

    private fun dismiss() {
        popup?.dismiss()
        sheet?.dismiss()
        removeAnchor()
        popup = null
        sheet = null
    }

    private fun showPopup(data: Data, rect: Rect): Boolean {
        val fragment = bridgeDelegate.destination.fragment
        val activity = fragment.activity ?: return false
        val webView = bridgeDelegate.destination.navigator.session.webView
        if (!webView.isAttachedToWindow) return false
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return false

        // CSS pixels are density-independent pixels at the page's zoom.
        val density = activity.resources.displayMetrics.density
        val webLocation = IntArray(2).also(webView::getLocationInWindow)
        val contentLocation = IntArray(2).also(content::getLocationInWindow)

        val view = View(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        content.addView(
            view,
            FrameLayout
                .LayoutParams(
                    (rect.width * density).toInt().coerceAtLeast(1),
                    (rect.height * density).toInt().coerceAtLeast(1)
                ).apply {
                    leftMargin = webLocation[0] - contentLocation[0] + (rect.x * density).toInt()
                    topMargin = webLocation[1] - contentLocation[1] + (rect.y * density).toInt()
                }
        )
        anchor = view

        val error = MaterialColors.getColor(view, AppCompatR.attr.colorError)
        val menu = PopupMenu(activity, view, Gravity.START)
        data.items.forEachIndexed { index, item ->
            val title = if (item.destructive == true) item.title.colored(error) else item.title
            menu.menu.add(0, index, index, title).apply {
                isEnabled = item.disabled != true
                SymbolDrawable.from(activity, item.image)?.let { symbol ->
                    val color =
                        if (item.destructive == true) {
                            error
                        } else {
                            MaterialColors.getColor(view, MaterialR.attr.colorOnSurfaceVariant)
                        }
                    symbol.setTintList(ColorStateList.valueOf(color))
                    icon = symbol
                }
            }
        }
        menu.setForceShowIcon(true)
        menu.setOnMenuItemClickListener { item ->
            choose(data.items[item.itemId], item.itemId)
            true
        }
        menu.setOnDismissListener { removeAnchor() }
        popup = menu
        view.post { if (popup === menu) menu.show() }
        return true
    }

    private fun showSheet(data: Data) {
        val context = bridgeDelegate.destination.fragment.context ?: return
        val actions =
            data.items.map {
                ActionSheet.Action(
                    title = it.title,
                    image = it.image,
                    destructive = it.destructive == true,
                    disabled = it.disabled == true
                )
            }
        val dialog = ActionSheet.show(context, data.title, actions) { index -> choose(data.items[index], index) }
        dialog.setOnDismissListener { if (sheet === dialog) sheet = null }
        sheet = dialog
    }

    private fun choose(item: Item, index: Int) {
        val clip = item.clip()
        if (clip == null) {
            replyTo("show", Selection(index))
            return
        }

        val activity = bridgeDelegate.destination.fragment.activity ?: return
        val clipboard = activity.getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(clip)
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            (activity as? SnackbarHost)?.showSnackbar(activity.getString(R.string.copied))
        }
    }

    private fun removeAnchor() {
        anchor?.let { (it.parent as? ViewGroup)?.removeView(it) }
        anchor = null
    }

    private fun String.colored(color: Int) = SpannableString(this).apply {
        setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Serializable
    data class Data(val items: List<Item> = emptyList(), val rect: Rect? = null, val title: String? = null)

    @Serializable
    data class Item(
        val title: String,
        @SerialName("androidImage") val image: String? = null,
        val destructive: Boolean? = null,
        val disabled: Boolean? = null,
        @SerialName("copy") val copyText: String? = null,
        /** The answer as HTML, copied beside [copyText] when present. */
        @SerialName("copyHtml") val copyHtml: String? = null,
        /** A stable name for the action, for native code that needs one; never the title. */
        val nativeAction: String? = null
    ) {
        /** The clip for a copy item: HTML and text together when there's HTML; null for other items. */
        fun clip(): ClipData? {
            val text = copyText ?: return null
            return if (copyHtml != null) {
                ClipData.newHtmlText(title, text, copyHtml)
            } else {
                ClipData.newPlainText(title, text)
            }
        }
    }

    @Serializable
    data class Rect(val x: Double, val y: Double, val width: Double, val height: Double)

    @Serializable
    data class Selection(val index: Int)

    companion object {
        val factory = BridgeComponentFactory("context-menu", ::ContextMenuComponent)
    }
}
