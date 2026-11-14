package com.cybersecurityrahbar.keyboardcyber.data

import android.content.Context

object KeyboardPreferences {
    private const val FILE_NAME = "keyboard_cyber_preferences"
    private const val KEY_ARCHIVE_SUGGESTIONS = "archive_suggestions_enabled"

    fun archiveSuggestionsEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARCHIVE_SUGGESTIONS, true)

    fun setArchiveSuggestionsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARCHIVE_SUGGESTIONS, enabled)
            .apply()
    }
}
