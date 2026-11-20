package com.chatwithwork.app.bridge

import com.chatwithwork.app.AppConfig
import com.masilotti.bridgecomponents.reviewprompt.ReviewPromptComponent
import com.masilotti.bridgecomponents.search.SearchComponent
import dev.hotwire.core.bridge.BridgeComponentFactory

/**
 * Every bridge component the app registers. The names are the contract with
 * the web (docs/server-contract.md): Hotwire Native lists them in the user
 * agent, and a Stimulus controller only connects when its name is there.
 *
 * The names and messages of Joe Masilotti's components are kept, so his
 * Stimulus controllers work as they are. Where his Android version draws its
 * own buttons, this app's version uses the native toolbar, menus, sheets,
 * dialogs, and snackbars instead. `review-prompt` and `search` are his,
 * unchanged. `auth-session`, `composer`, `context-menu`, `file`, `notification-token`, `sidebar`, and
 * `web-composer` are Chat with Work's own, with the same names and messages as
 * in the iOS app, so the server serves both apps the same pages.
 */
object BridgeComponents {
    private val every =
        arrayOf(
            AlertComponent.factory,
            AuthSessionComponent.factory,
            ButtonComponent.factory,
            ComposerComponent.factory,
            ContextMenuComponent.factory,
            FileComponent.factory,
            FormComponent.factory,
            HapticComponent.factory,
            MenuComponent.factory,
            NotificationTokenComponent.factory,
            BridgeComponentFactory("review-prompt", ::ReviewPromptComponent),
            BridgeComponentFactory("search", ::SearchComponent),
            ShareComponent.factory,
            SidebarComponent.factory,
            ThemeComponent.factory,
            ToastComponent.factory,
            WebComposerComponent.factory
        )

    /**
     * What the app registers: every component, but `composer` only while the
     * app draws its own (AppConfig.NATIVE_COMPOSER), and `web-composer` only
     * while it doesn't, so the page's composer says what the app needs.
     */
    val all = registered(nativeComposer = AppConfig.NATIVE_COMPOSER)

    fun registered(nativeComposer: Boolean) = every.filter {
        when (it.name) {
            ComposerComponent.NAME -> nativeComposer
            WebComposerComponent.NAME -> !nativeComposer
            else -> true
        }
    }.toTypedArray()
}
