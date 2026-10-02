package com.chatwithwork.app.main

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.chatwithwork.app.R
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.tabs.HotwireBottomTab

/**
 * The bottom bar's tabs. Each has its own navigator, so each keeps its own
 * history. They start on unprefixed paths, which the server redirects into
 * the organization the person used last, until the person moves to another
 * organization: then they start inside it.
 */
enum class MainTab(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @IdRes val navigatorHostId: Int,
    val path: String
) {
    CHATS(R.string.tab_chats, R.drawable.ic_tab_chats, R.id.chats_navigator_host, "/chats"),
    PROJECTS(R.string.tab_projects, R.drawable.ic_tab_projects, R.id.projects_navigator_host, "/projects"),
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

    fun bottomTab(context: Context) = HotwireBottomTab(
        title = context.getString(title),
        iconResId = icon,
        configuration = configuration
    )

    companion object {
        fun bottomTabs(context: Context): List<HotwireBottomTab> = entries.map { it.bottomTab(context) }

        /** The tab a link belongs in, or null to keep it where it was opened. */
        fun forUrl(url: String): MainTab? = when (AppUrls.section(url)) {
            AppUrls.Section.CHATS -> CHATS
            AppUrls.Section.PROJECTS -> PROJECTS
            AppUrls.Section.SETTINGS -> SETTINGS
            AppUrls.Section.OTHER -> null
        }

        fun forNavigatorName(name: String): MainTab? = entries.firstOrNull { it.configuration.name == name }
    }
}
