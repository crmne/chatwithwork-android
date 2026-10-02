package com.chatwithwork.app.main

import android.content.Context
import androidx.core.content.edit

/**
 * The little the app remembers between launches about the session the web
 * views hold: whether someone signed in, and which organization they were
 * in. The session itself is the server's cookie in the web view's cookie
 * store; this only decides what to show before the first page answers.
 */
class SessionMemory(context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var hasSignedIn: Boolean
        get() = preferences.getBoolean(KEY_SIGNED_IN, false)
        set(value) = preferences.edit { putBoolean(KEY_SIGNED_IN, value) }

    var lastAccount: String?
        get() = preferences.getString(KEY_ACCOUNT, null)
        set(value) = preferences.edit { putString(KEY_ACCOUNT, value) }

    private companion object {
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_ACCOUNT = "account"
    }
}
