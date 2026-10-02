package com.chatwithwork.app.fragments

import com.chatwithwork.app.AppConfig

/**
 * Page titles for the top app bar. The web's `<title>` is written for a
 * browser tab: staging prefixes "Staging · " and some pages add
 * " | Chat with Work". The bar needs only the page's own name; the server
 * sends that to the app (docs/server-contract.md), and this tidies titles
 * from a server that doesn't yet.
 */
object PageTitles {
    private val appSuffix = Regex("\\s+[|·-]\\s+Chat with Work$")
    private val environmentPrefix = Regex("^(Staging|Development)\\s+·\\s+", RegexOption.IGNORE_CASE)

    fun clean(title: String?): String {
        var result = title.orEmpty().trim()
        if (AppConfig.environment != "production") result = environmentPrefix.replace(result, "")
        result = appSuffix.replace(result, "")
        return result
    }
}
