package com.chatwithwork.app.push

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.chatwithwork.app.R

/**
 * The built-in services a Live Update can show: `Activity::SERVICE_NAMES` in
 * the Rails app without its own services and the directory's, plus a person's
 * MCP servers and computers, which only ever show as generic drawings. A key
 * that isn't here shows nothing, so a server's value can't reach the card.
 */
object LiveServices {
    data class Service(val name: String, @param:DrawableRes val logo: Int, @param:ColorInt val brand: Int)

    private val all =
        mapOf(
            "google_drive" to Service("Drive", R.drawable.live_service_google_drive, 0xFF1FA463.toInt()),
            "gmail" to Service("Gmail", R.drawable.live_service_gmail, 0xFFEA4335.toInt()),
            "google_calendar" to Service("Calendar", R.drawable.live_service_google_calendar, 0xFF4285F4.toInt()),
            "dropbox" to Service("Dropbox", R.drawable.live_service_dropbox, 0xFF0061FF.toInt()),
            "onedrive" to Service("OneDrive", R.drawable.live_service_onedrive, 0xFF0078D4.toInt()),
            "sharepoint" to Service("SharePoint", R.drawable.live_service_sharepoint, 0xFF038387.toInt()),
            "outlook" to Service("Outlook", R.drawable.live_service_outlook, 0xFF0F6CBD.toInt()),
            "teams" to Service("Teams", R.drawable.live_service_teams, 0xFF5B5FC7.toInt()),
            "nextcloud" to Service("Nextcloud", R.drawable.live_service_nextcloud, 0xFF0082C9.toInt()),
            "odoo" to Service("Odoo", R.drawable.live_service_odoo, 0xFF714B67.toInt()),
            "chatwoot" to Service("Chatwoot", R.drawable.live_service_chatwoot, 0xFF1F93FF.toInt()),
            "slack" to Service("Slack", R.drawable.live_service_slack, 0xFFE01E5A.toInt()),
            "discord" to Service("Discord", R.drawable.live_service_discord, 0xFF5865F2.toInt()),
            "sendfox" to Service("SendFox", R.drawable.live_service_sendfox, 0xFFFF5A36.toInt()),
            "tidycal" to Service("TidyCal", R.drawable.live_service_tidycal, 0xFF2563EB.toInt()),
            "x" to Service("X", R.drawable.live_service_x, 0xFF000000.toInt()),
            "basecamp" to Service("Basecamp", R.drawable.live_service_basecamp, 0xFF1D2D35.toInt()),
            "notion" to Service("Notion", R.drawable.live_service_notion, 0xFF000000.toInt()),
            "todoist" to Service("Todoist", R.drawable.live_service_todoist, 0xFFE44332.toInt()),
            "github" to Service("GitHub", R.drawable.live_service_github, 0xFF24292F.toInt()),
            "bugsink" to Service("Bugsink", R.drawable.live_service_bugsink, 0xFFE33B3B.toInt()),
            "linear" to Service("Linear", R.drawable.live_service_linear, 0xFF5E6AD2.toInt()),
            "atlassian" to Service("Jira and Confluence", R.drawable.live_service_atlassian, 0xFF0052CC.toInt()),
            "granola" to Service("Granola", R.drawable.live_service_granola, 0xFFAEC23A.toInt()),
            "fireflies" to Service("Fireflies", R.drawable.live_service_fireflies, 0xFFE91E8C.toInt()),
            "hubspot" to Service("HubSpot", R.drawable.live_service_hubspot, 0xFFFF7A59.toInt()),
            "salesforce" to Service("Salesforce", R.drawable.live_service_salesforce, 0xFF00A1E0.toInt()),
            "intercom" to Service("Intercom", R.drawable.live_service_intercom, 0xFF1F2933.toInt()),
            "asana" to Service("Asana", R.drawable.live_service_asana, 0xFFF06A6A.toInt()),
            "monday" to Service("monday.com", R.drawable.live_service_monday, 0xFFFF3D57.toInt()),
            "figma" to Service("Figma", R.drawable.live_service_figma, 0xFFF24E1E.toInt()),
            "lattice" to Service("Lattice", R.drawable.live_service_lattice, 0xFF2AB7A9.toInt()),
            "netsuite" to Service("NetSuite", R.drawable.live_service_netsuite, 0xFF125580.toInt()),
            "chargebee" to Service("Chargebee", R.drawable.live_service_chargebee, 0xFFFF3300.toInt()),
            "databricks" to Service("Databricks", R.drawable.live_service_databricks, 0xFFFF3621.toInt()),
            "looker" to Service("Looker", R.drawable.live_service_looker, 0xFF4285F4.toInt()),
            "pidgeon" to Service("Pidgeon", R.drawable.live_service_pidgeon, 0xFF1F2933.toInt()),
            "mcp" to Service("", R.drawable.live_service_mcp, 0xFF8E93A0.toInt()),
            "computer" to Service("", R.drawable.live_service_computer, 0xFF8E93A0.toInt())
        )

    fun of(key: String?): Service? = all[key]
}
