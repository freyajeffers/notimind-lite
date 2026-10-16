package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AutomationPreferencesManager(context: Context, private val profileManager: ProfilePreferencesManager) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(readRules())
    val rules: StateFlow<List<AutoExecuteRule>> = _rules.asStateFlow()

    fun reload() {
        _rules.value = readRules()
    }

    fun addRule(rule: AutoExecuteRule) {
        setRules(_rules.value + rule)
    }

    fun removeRule(id: String) {
        setRules(_rules.value.filterNot { it.id == id })
    }

    fun setRules(rules: List<AutoExecuteRule>) {
        val safe = rules.distinctBy { it.id }
        preferences.edit().putString(KEY_RULES, encodeRules(safe)).apply()
        _rules.value = safe
    }

    private fun readRules(): List<AutoExecuteRule> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RULES, "[]"))
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            AutoExecuteRule(
                id = value.optString("id"),
                packageName = value.optString("packageName"),
                titleContains = value.optString("titleContains"),
                actionIndex = value.optInt("actionIndex", 0).coerceAtLeast(0),
                enabled = value.optBoolean("enabled", true)
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeRules(rules: List<AutoExecuteRule>): String = JSONArray(rules.map { rule ->
        JSONObject()
            .put("id", rule.id)
            .put("packageName", rule.packageName)
            .put("titleContains", rule.titleContains)
            .put("actionIndex", rule.actionIndex)
            .put("enabled", rule.enabled)
    }).toString()

    private companion object {
        const val KEY_RULES = "config_auto_execute_rules"
    }
}
