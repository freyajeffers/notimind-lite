package com.jeffers.notimindlite.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AutomationPreferencesManager(
    context: Context,
    private val profileManager: ProfilePreferencesManager
) {
    private val appContext = context.applicationContext
    private val preferences get() = appContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(readRules())
    private val _autoActOnNotification = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, false)
    )
    private val _defaultReplyMethod = MutableStateFlow(
        preferences.getString(KEY_DEFAULT_REPLY_METHOD, "inline") ?: "inline"
    )
    private val _longPressAction = MutableStateFlow(
        preferences.getString(KEY_LONG_PRESS_ACTION, "open") ?: "open"
    )
    val rules: StateFlow<List<AutoExecuteRule>> = _rules.asStateFlow()
    val autoActOnNotification: StateFlow<Boolean> = _autoActOnNotification.asStateFlow()
    val defaultReplyMethod: StateFlow<String> = _defaultReplyMethod.asStateFlow()
    val longPressAction: StateFlow<String> = _longPressAction.asStateFlow()

    fun reload() {
        _rules.value = readRules()
        _autoActOnNotification.value = preferences.getBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, false)
        _defaultReplyMethod.value = preferences.getString(KEY_DEFAULT_REPLY_METHOD, "inline") ?: "inline"
        _longPressAction.value = preferences.getString(KEY_LONG_PRESS_ACTION, "open") ?: "open"
    }

    fun addRule(rule: AutoExecuteRule) {
        setRules(_rules.value + rule)
    }

    fun removeRule(id: String) {
        setRules(_rules.value.filterNot { it.id == id })
    }

    fun setRules(rules: List<AutoExecuteRule>) {
        val safe = rules.distinctBy { it.id }
        preferences.edit {putString(KEY_RULES, encodeRules(safe))}
        _rules.value = safe
    }

    fun setAutoActOnNotification(value: Boolean) {
        preferences.edit {putBoolean(KEY_AUTO_ACT_ON_NOTIFICATION, value)}
        _autoActOnNotification.value = value
    }

    fun setDefaultReplyMethod(value: String) {
        val safe = if (value in REPLY_METHODS) value else "inline"
        preferences.edit {putString(KEY_DEFAULT_REPLY_METHOD, safe)}
        _defaultReplyMethod.value = safe
    }

    fun setLongPressAction(value: String) {
        val safe = if (value in LONG_PRESS_ACTIONS) value else "open"
        preferences.edit {putString(KEY_LONG_PRESS_ACTION, safe)}
        _longPressAction.value = safe
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

    private fun encodeRules(rules: List<AutoExecuteRule>): String = JSONArray(
        rules.map { rule ->
            JSONObject()
                .put("id", rule.id)
                .put("packageName", rule.packageName)
                .put("titleContains", rule.titleContains)
                .put("actionIndex", rule.actionIndex)
                .put("enabled", rule.enabled)
        }
    ).toString()

    private companion object {
        const val KEY_RULES = "config_auto_execute_rules"
        const val KEY_AUTO_ACT_ON_NOTIFICATION = "config_auto_act_on_notification"
        const val KEY_DEFAULT_REPLY_METHOD = "config_default_reply_method"
        const val KEY_LONG_PRESS_ACTION = "config_long_press_action"
        val REPLY_METHODS = setOf("inline", "open_app", "copy")
        val LONG_PRESS_ACTIONS = setOf("open", "reply", "dismiss")
    }
}
