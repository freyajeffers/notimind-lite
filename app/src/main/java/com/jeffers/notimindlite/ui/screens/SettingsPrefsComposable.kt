package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.DbEncryptionMode
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.BuildConfig

@Composable
fun settingsPreferencesSection(preferencesRepository: PreferencesRepository) {

    val autoActOnNotification by preferencesRepository.autoActOnNotification.collectAsState(initial = false)
    val defaultReplyMethod by preferencesRepository.defaultReplyMethod.collectAsState(initial = "inline")
    val longPressAction by preferencesRepository.longPressAction.collectAsState(initial = "open")
    val autoExecuteRules by preferencesRepository.autoExecuteRules.collectAsState(initial = emptyList())
    var rulePackage by remember { mutableStateOf("") }
    var ruleTitle by remember { mutableStateOf("") }
    var ruleAction by remember { mutableStateOf("0") }

    // display options moved to DisplaySettingsSection

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            searchSyncSection(preferencesRepository)
            displaySettingsSection(preferencesRepository)

            Text(stringResource(R.string.pref_automation_title), style = MaterialTheme.typography.titleMedium)
            preferenceSwitchRow(
                stringResource(R.string.pref_auto_act_title),
                stringResource(R.string.pref_auto_act_summary), autoActOnNotification,
                preferencesRepository::setAutoActOnNotification, enabled = !BuildConfig.DEBUG
            )
            preferenceSelectRow(
                stringResource(R.string.pref_default_reply_title),
                defaultReplyMethod,
                replyOptions,
                preferencesRepository::setDefaultReplyMethod,
                enabled = !BuildConfig.DEBUG
            )
            preferenceSelectRow(
                stringResource(R.string.pref_long_press_title),
                longPressAction,
                longPressOptions,
                preferencesRepository::setLongPressAction,
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

            settingsSecuritySection(preferencesRepository)

            debugSettingsSection(preferencesRepository)
        }
    }
}


@Composable
internal fun disabledPreferenceRow(title: String, summary: String) {
    preferenceSwitchRow(title, summary, checked = false, onCheckedChange = {}, enabled = false)
}

@Composable
internal fun preferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

@Composable
internal fun preferenceSelectRow(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title }
            ) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        enabled = enabled,
                        onClick = {
                            onSelected(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
