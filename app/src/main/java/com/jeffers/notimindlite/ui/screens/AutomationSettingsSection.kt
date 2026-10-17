package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository

@Composable
fun automationSettingsSection(preferencesRepository: PreferencesRepository) {
    val autoActOnNotification by preferencesRepository.automationManager
        .autoActOnNotification.collectAsState(initial = false)
    val defaultReplyMethod by preferencesRepository.automationManager
        .defaultReplyMethod.collectAsState(initial = "inline")
    val longPressAction by preferencesRepository.automationManager
        .longPressAction.collectAsState(initial = "open")
    val autoExecuteRules by preferencesRepository.autoExecuteRules.collectAsState(initial = emptyList())
    var rulePackage by remember { mutableStateOf("") }
    var ruleTitle by remember { mutableStateOf("") }
    var ruleAction by remember { mutableStateOf("0") }

    val replyOptions = listOf(
        "inline" to stringResource(R.string.pref_reply_inline),
        "open_app" to stringResource(R.string.pref_reply_open_app),
        "copy" to stringResource(R.string.pref_reply_copy)
    )

    val longPressOptions = listOf(
        "open" to stringResource(R.string.pref_action_open),
        "reply" to stringResource(R.string.pref_action_reply),
        "dismiss" to stringResource(R.string.pref_action_dismiss)
    )

    Column {
        Text(stringResource(R.string.pref_automation_title), style = MaterialTheme.typography.titleMedium)
        preferenceSwitchRow(
            stringResource(R.string.pref_auto_act_title),
            stringResource(R.string.pref_auto_act_summary), autoActOnNotification,
            preferencesRepository.automationManager::setAutoActOnNotification, enabled = !BuildConfig.DEBUG
        )
        preferenceSelectRow(
            stringResource(R.string.pref_default_reply_title),
            defaultReplyMethod,
            replyOptions,
            preferencesRepository.automationManager::setDefaultReplyMethod,
            enabled = !BuildConfig.DEBUG
        )
        preferenceSelectRow(
            stringResource(R.string.pref_long_press_title),
            longPressAction,
            longPressOptions,
            preferencesRepository.automationManager::setLongPressAction,
            enabled = !BuildConfig.DEBUG
        )
        autoRulesSection(
            preferencesRepository = preferencesRepository,
            config = AutoRulesConfig(
                autoExecuteRules = autoExecuteRules,
                rulePackage = rulePackage,
                onRulePackageChange = { rulePackage = it },
                ruleTitle = ruleTitle,
                onRuleTitleChange = { ruleTitle = it },
                ruleAction = ruleAction,
                onRuleActionChange = { ruleAction = it }
            )
        )
    }
}
