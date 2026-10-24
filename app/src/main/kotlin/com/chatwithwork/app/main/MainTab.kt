package com.chatwithwork.app.main

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.chatwithwork.app.R
import com.chatwithwork.app.routing.AppUrls
import com.chatwithwork.app.shell.ShellTab
import dev.hotwire.navigation.navigator.NavigatorConfiguration

/**
 * The floating toolbar's tabs: Chats (with its list switcher, which picks a
 * project too), Bots, and Settings, the person's own. New chat is the
 * floating button beside them. Each tab has its own navigator, so each keeps
 * its own history. They start on unprefixed paths, which the server
 * redirects into the organization the person used last, until the person
 * moves to another organization: then they start inside it.
 *
 * The server names the tabs a person may use (a guest has no Bots) in each
 * page's native-tabs meta tag (docs/server-contract.md).
 */
enum class MainTab(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @IdRes val navigatorHostId: Int,
    val path: String
) {
    CHATS(R.string.tab_chats, R.drawable.ic_tab_chats, R.id.chats_navigator_host, "/chats"),
    BOTS(R.string.tab_bots, R.drawable.ic_tab_bots, R.id.bots_navigator_host, "/bots"),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings, R.id.settings_navigator_host, "/settings")
    ;

    /** The tab's navigator, starting where the server picks the organization. */
    val configuration = configuration(account = null)

    /** The tab's navigator, starting inside [account] when it's given. */
    fun configuration(account: String?) = NavigatorConfiguration(
        name = name.lowercase(),
        startLocation = AppUrls.page(path, account),
        navigatorHostId = navigatorHostId
    )

    val shellTab = ShellTab(title = title, icon = icon, hostId = navigatorHostId)

    /** The name pages use for the tab in their native-tabs meta tag. */
    val key: String get() = configuration.name

    companion object {
        /** The tab a link belongs in, or null to keep it where it was opened. */
        fun forUrl(url: String): MainTab? = when (AppUrls.section(url)) {
            AppUrls.Section.CHATS -> CHATS
            AppUrls.Section.BOTS -> BOTS
            AppUrls.Section.SETTINGS -> SETTINGS
            AppUrls.Section.OTHER -> null
        }

        fun forNavigatorName(name: String): MainTab? = entries.firstOrNull { it.configuration.name == name }

        fun forShellTab(tab: ShellTab): MainTab = entries.first { it.shellTab === tab }

        /**
         * The tabs a page's native-tabs meta tag ([content], like "chats
         * bots settings") leaves out. Chats and Settings always show; a page
         * without the tag (an older server) hides nothing.
         */
        fun hiddenBy(content: String?): Set<MainTab> {
            val named = content?.split(' ')?.filter { it.isNotBlank() }?.toSet() ?: return emptySet()
            return entries.filter { it != CHATS && it != SETTINGS && it.key !in named }.toSet()
        }
    }
}
