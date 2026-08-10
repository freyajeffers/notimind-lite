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
}
