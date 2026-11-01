package com.chatwithwork.app.push

import androidx.annotation.DrawableRes
import com.chatwithwork.app.R

/**
 * The built-in services a Live Update can show: `Activity::SERVICE_NAMES` in
 * the Rails app without its own services and the directory's, plus a person's
 * MCP servers and computers, which only ever show as generic drawings. A key
 * that isn't here shows nothing, so a server's value can't reach the card.
 */
object LiveServices {
    data class Service(val name: String, @param:DrawableRes val logo: Int)

    private val all =
        mapOf(
            "google_drive" to Service("Drive", R.drawable.live_service_google_drive),
            "gmail" to Service("Gmail", R.drawable.live_service_gmail),
            "google_calendar" to Service("Calendar", R.drawable.live_service_google_calendar),
            "dropbox" to Service("Dropbox", R.drawable.live_service_dropbox),
            "onedrive" to Service("OneDrive", R.drawable.live_service_onedrive),
            "sharepoint" to Service("SharePoint", R.drawable.live_service_sharepoint),
            "outlook" to Service("Outlook", R.drawable.live_service_outlook),
            "teams" to Service("Teams", R.drawable.live_service_teams),
            "nextcloud" to Service("Nextcloud", R.drawable.live_service_nextcloud),
            "odoo" to Service("Odoo", R.drawable.live_service_odoo),
            "chatwoot" to Service("Chatwoot", R.drawable.live_service_chatwoot),
            "slack" to Service("Slack", R.drawable.live_service_slack),
            "discord" to Service("Discord", R.drawable.live_service_discord),
            "sendfox" to Service("SendFox", R.drawable.live_service_sendfox),
            "tidycal" to Service("TidyCal", R.drawable.live_service_tidycal),
            "x" to Service("X", R.drawable.live_service_x),
            "basecamp" to Service("Basecamp", R.drawable.live_service_basecamp),
            "notion" to Service("Notion", R.drawable.live_service_notion),
            "todoist" to Service("Todoist", R.drawable.live_service_todoist),
            "github" to Service("GitHub", R.drawable.live_service_github),
            "bugsink" to Service("Bugsink", R.drawable.live_service_bugsink),
            "linear" to Service("Linear", R.drawable.live_service_linear),
            "atlassian" to Service("Jira and Confluence", R.drawable.live_service_atlassian),
            "granola" to Service("Granola", R.drawable.live_service_granola),
            "fireflies" to Service("Fireflies", R.drawable.live_service_fireflies),
            "hubspot" to Service("HubSpot", R.drawable.live_service_hubspot),
            "salesforce" to Service("Salesforce", R.drawable.live_service_salesforce),
            "intercom" to Service("Intercom", R.drawable.live_service_intercom),
            "asana" to Service("Asana", R.drawable.live_service_asana),
            "monday" to Service("monday.com", R.drawable.live_service_monday),
            "figma" to Service("Figma", R.drawable.live_service_figma),
            "lattice" to Service("Lattice", R.drawable.live_service_lattice),
            "netsuite" to Service("NetSuite", R.drawable.live_service_netsuite),
            "chargebee" to Service("Chargebee", R.drawable.live_service_chargebee),
            "databricks" to Service("Databricks", R.drawable.live_service_databricks),
            "looker" to Service("Looker", R.drawable.live_service_looker),
            "pidgeon" to Service("Pidgeon", R.drawable.live_service_pidgeon),
            "mcp" to Service("", R.drawable.live_service_mcp),
            "computer" to Service("", R.drawable.live_service_computer)
        )

    /** Single-colour logos, drawn in the card's own ink on whatever they sit on. */
    private val monochrome = setOf("basecamp", "discord", "github", "intercom", "pidgeon", "x", "mcp", "computer")

    fun of(key: String?): Service? = all[key]

    fun isMonochrome(key: String?): Boolean = key in monochrome
}
