package com.jeffers.notimindlite.data.local

import android.content.Context

class PreferenceManager(context: Context) {
    private val prefs = context.getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)

    fun getExpandedSection(): String {
        val saved = prefs.getString("expanded_section", "ACTIVE") ?: "ACTIVE"
        return if (saved == "NONE") "ACTIVE" else saved
    }

    fun setExpandedSection(section: String) {
        prefs.edit().putString("expanded_section", section).apply()
    }

    fun isRestoreOnBootEnabled(): Boolean {
        return prefs.getBoolean("restore_on_boot", false)
    }

    fun setRestoreOnBootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("restore_on_boot", enabled).apply()
    }

    // Preference for storing last app update timestamp
    private val PREF_LAST_UPDATE_TIME = "pref_last_update_time"

    fun setLastUpdateTime(time: Long) {
        prefs.edit().putLong(PREF_LAST_UPDATE_TIME, time).apply()
    }

    fun getLastUpdateTime(): Long {
        return prefs.getLong(PREF_LAST_UPDATE_TIME, 0L)
    }
}
