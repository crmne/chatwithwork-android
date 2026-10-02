package com.chatwithwork.app.bridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeComponentJsonConverter
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable

/**
 * `share`: Android's share sheet for a link. Joe Masilotti's contract plus
 * the iOS app's additions:
 *
 * - `connect` with `{url?, title?, text?, color?}` adds a share action to the
 *   top app bar; `disconnect` removes it. `url` defaults to the page.
 * - `share` with the same data opens the sheet at once (after the page made a
 *   share link, say) and replies `{completed, activityType}` when the person
 *   is back: `completed` is whether they picked an app, `activityType` that
 *   app's package name.
 *
 * The title labels the sheet's preview; the text goes before the link.
 */
class ShareComponent(name: String, delegate: BridgeDelegate<HotwireDestination>) : ToolbarComponent(name, delegate) {
    override val menuGroup = GROUP_SHARE

    private var button: Data? = null

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                button = message.data<Data>()
                redraw()
            }

            "share" -> {
                message.data<Data>()?.let { share(it, message) }
            }

            "disconnect" -> {
                button = null
                clear()
            }
        }
    }

    override fun render(toolbar: Toolbar) {
        val data = button ?: return
        val title = toolbar.context.getString(R.string.share)

        toolbar.menu.add(menuGroup, ITEM_ID, ORDER_SHARE, title).apply {
            icon = SymbolDrawable(toolbar.context, "share")
            tint(iconColor(toolbar, data.color))
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            describe(title)
            setOnMenuItemClickListener {
                share(data, message = null)
                true
            }
        }
    }

    private fun share(data: Data, message: Message?) {
        val activity = destination.fragment.activity ?: return
        val url = data.url?.takeIf { it.isNotBlank() } ?: destination.navigator.session.webView.url ?: return
        val text = listOfNotNull(data.text?.takeIf { it.isNotBlank() }, url).joinToString("\n\n")
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                data.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            }

        if (message == null) {
            activity.startActivity(Intent.createChooser(send, null))
            return
        }

        // Hear which app was picked, so the page can tell a share from a dismissal.
        val answered = AtomicBoolean(false)
        val action = "${activity.packageName}.SHARE_CHOSEN.${System.nanoTime()}"
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (answered.compareAndSet(false, true)) {
                        reply(message, Result(completed = true, activityType = intent.chosenComponent()?.packageName))
                    }
                    runCatching { context.applicationContext.unregisterReceiver(this) }
                }
            }
        ContextCompat.registerReceiver(
            activity.applicationContext,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val callback =
            PendingIntent.getBroadcast(
                activity,
                0,
                Intent(action).setPackage(activity.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        (activity as? BrowserReturnHost)?.onNextReturn {
            if (answered.compareAndSet(false, true)) reply(message, Result(completed = false))
            runCatching { activity.applicationContext.unregisterReceiver(receiver) }
        }
        activity.startActivity(Intent.createChooser(send, null, callback.intentSender))
    }

    private fun reply(message: Message, result: Result) {
        replyWith(message.replacing(jsonData = BridgeComponentJsonConverter.toJson(result)))
    }

    private fun Intent.chosenComponent(): ComponentName? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
    }

    @Serializable
    data class Data(
        val url: String? = null,
        val title: String? = null,
        val text: String? = null,
        val color: String? = null
    )

    @Serializable
    data class Result(val completed: Boolean, val activityType: String? = null)

    companion object {
        private const val ITEM_ID = 0x0C400

        val factory = BridgeComponentFactory("share", ::ShareComponent)
    }
}
