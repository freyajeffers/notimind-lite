package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.UserManager
import androidx.core.content.edit

@Suppress("TooManyFunctions") // Compatibility facade preserves the established preference API.
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
        prefs.edit { putString("expanded_section", section) }
    }

    fun isRestoreOnBootEnabled(): Boolean {
        return prefs.getBoolean("restore_on_boot", true)
    }

    fun setRestoreOnBootEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("restore_on_boot", enabled) }
    }

    fun isStrictPrivacyEnabled(): Boolean {
        return prefs.getBoolean("strict_privacy", false)
    }

    fun setStrictPrivacyEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("strict_privacy", enabled) }
    }

    private val PREF_LAST_UPDATE_TIME = "pref_last_update_time"

    fun setLastUpdateTime(time: Long) {
        prefs.edit { putLong(PREF_LAST_UPDATE_TIME, time) }
    }

    // Toggle for PII redaction (disabled by default)
    fun isPiiRedactionEnabled(): Boolean {
        return prefs.getBoolean("pii_redaction_enabled", false)
    }

    fun setPiiRedactionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("pii_redaction_enabled", enabled) }
    }

    fun getThreadPoolSize() = PreferencesRepository(effectiveContext).getThreadPoolSize()
    fun setThreadPoolSize(value: Int) = PreferencesRepository(effectiveContext).setThreadPoolSize(value)
    fun getEmbeddingRateMs() = PreferencesRepository(effectiveContext).getEmbeddingRateMs()
    fun setEmbeddingRateMs(value: Long) = PreferencesRepository(effectiveContext).setEmbeddingRateMs(value)
    fun getDbCompactionDays() = PreferencesRepository(effectiveContext).getDbCompactionDays()
    fun setDbCompactionDays(value: Int) = PreferencesRepository(effectiveContext).setDbCompactionDays(value)
    fun getReindexDays() = PreferencesRepository(effectiveContext).getReindexDays()
    fun setReindexDays(value: Int) = PreferencesRepository(effectiveContext).setReindexDays(value)
    fun isEmbeddingOffloadEnabled() = PreferencesRepository(effectiveContext).isEmbeddingOffloadEnabled()
    fun setEmbeddingOffloadEnabled(value: Boolean) =
        PreferencesRepository(effectiveContext).setEmbeddingOffloadEnabled(value)
}
