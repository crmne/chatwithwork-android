package com.chatwithwork.app.bridge

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
 * unchanged. `auth-session`, `context-menu`, and `notification-token` are
 * Chat with Work's own, with the same names and messages as in the iOS app,
 * so the server serves both apps the same pages.
 */
object BridgeComponents {
    val all =
        arrayOf(
            AlertComponent.factory,
            AuthSessionComponent.factory,
            ButtonComponent.factory,
            ContextMenuComponent.factory,
            FormComponent.factory,
            HapticComponent.factory,
            MenuComponent.factory,
            NotificationTokenComponent.factory,
            BridgeComponentFactory("review-prompt", ::ReviewPromptComponent),
            BridgeComponentFactory("search", ::SearchComponent),
            ShareComponent.factory,
            ThemeComponent.factory,
            ToastComponent.factory
        )
}
