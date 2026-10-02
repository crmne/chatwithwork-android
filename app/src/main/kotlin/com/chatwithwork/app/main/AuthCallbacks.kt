package com.chatwithwork.app.main

/**
 * The one browser sign-in in flight, waiting for its chatwithwork:// callback.
 * The callback arrives as an intent the activity receives; the bridge
 * component that started the sign-in is waiting for it here.
 */
object AuthCallbacks {
    private var pending: ((String) -> Unit)? = null

    fun begin(onCallback: (String) -> Unit) {
        pending = onCallback
    }

    /** Hands [url] to the sign-in waiting for it. False when none was. */
    fun deliver(url: String): Boolean {
        val callback = pending ?: return false
        pending = null
        callback(url)
        return true
    }

    /** Drops the sign-in in flight. True when there was one. */
    fun cancel(): Boolean {
        val had = pending != null
        pending = null
        return had
    }
}
