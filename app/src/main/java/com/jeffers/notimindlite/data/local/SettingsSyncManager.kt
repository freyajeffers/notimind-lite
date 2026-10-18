package com.jeffers.notimindlite.data.local

import android.content.SharedPreferences
import org.json.JSONObject

class SettingsSyncManager(
    private val preferences: () -> SharedPreferences
) {
    fun exportSyncableSettings(): JSONObject {
        val result = JSONObject()
        preferences().all.forEach { (key, value) ->
            if (!key.startsWith("config_") || key == "config_use_supabase_sync") {
                when (value) {
                    is Boolean, is Int, is Long, is Float, is String -> result.put(key, value)
                }
            }
        }
        return result
    }

    fun importSyncableSettings(settings: JSONObject) {
        val editor = preferences().edit()
        settings.keys().forEach { key ->
            if (!key.startsWith("config_") || key == "config_use_supabase_sync") {
                when (val value = settings.opt(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                }
            }
        }
        editor.apply()
    }
}
