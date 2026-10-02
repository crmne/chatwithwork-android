package com.chatwithwork.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Light, dark, or the system's choice, for the native bars and the web views
 * alike (the web views follow the app theme's light or dark).
 */
object AppTheme {
    enum class Mode(val nightMode: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
        ;

        companion object {
            fun fromWeb(value: String?): Mode = when (value) {
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
        }
    }

    private const val PREFERENCES = "theme"
    private const val KEY_MODE = "mode"

    /** Applies the saved mode. Call before any activity is created. */
    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(saved(context).nightMode)
    }

    /** Switches to [mode] and remembers it. Recreates activities if it changes. */
    fun apply(context: Context, mode: Mode) {
        if (saved(context) == mode && AppCompatDelegate.getDefaultNightMode() == mode.nightMode) return

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_MODE, mode.name)
        }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private fun saved(context: Context): Mode {
        val name = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return Mode.entries.firstOrNull { it.name == name } ?: Mode.SYSTEM
    }
}
