package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.UserManager

class PreferenceManager(context: Context) {

    private val effectiveContext: Context = run {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    private val prefs = effectiveContext.getSharedPreferences("notimind_lite_prefs", Context.MODE_PRIVATE)

    fun getExpandedSection(): String {
        return prefs.getString("expanded_section", "ACTIVE") ?: "ACTIVE"
    }

    fun setExpandedSection(section: String) {
        prefs.edit().putString("expanded_section", section).apply()
    }

    fun isRestoreOnBootEnabled(): Boolean {
        return prefs.getBoolean("restore_on_boot", true)
    }

    fun setRestoreOnBootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("restore_on_boot", enabled).apply()
    }

    fun isStrictPrivacyEnabled(): Boolean {
        return prefs.getBoolean("strict_privacy", false)
    }

    fun setStrictPrivacyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("strict_privacy", enabled).apply()
    }

    private val PREF_LAST_UPDATE_TIME = "pref_last_update_time"

    fun setLastUpdateTime(time: Long) {
        prefs.edit().putLong(PREF_LAST_UPDATE_TIME, time).apply()
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean("has_completed_onboarding", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("has_completed_onboarding", completed).apply()
    }
}
