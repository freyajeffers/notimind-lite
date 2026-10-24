package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.PreferencesRepository

@Composable
fun AutoRulesSection(preferencesRepository: PreferencesRepository, config: AutoRulesConfig) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                config.rulePackage, onValueChange = config.onRulePackageChange, Modifier.weight(1f), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_package)) }
            )
            OutlinedTextField(
                config.ruleTitle, onValueChange = config.onRuleTitleChange, Modifier.weight(1f), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_title)) }
            )
            OutlinedTextField(
                config.ruleAction, onValueChange = config.onRuleActionChange, Modifier.width(72.dp), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_action)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Button(onClick = {
            preferencesRepository.automationManager.addRule(
                AutoExecuteRule(
                    packageName = config.rulePackage.trim(), titleContains = config.ruleTitle.trim(),
                    actionIndex = config.ruleAction.toIntOrNull()?.coerceAtLeast(0) ?: 0
                )
            )
            config.onRulePackageChange("")
            config.onRuleTitleChange("")
            config.onRuleActionChange("0")
        }, enabled = !BuildConfig.DEBUG) { Text(stringResource(R.string.pref_auto_rule_add)) }

        config.autoExecuteRules.forEach { rule ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${rule.packageName.ifBlank { stringResource(R.string.pref_auto_rule_any_app) }} · " +
                        "${rule.titleContains.ifBlank { stringResource(R.string.pref_auto_rule_any_title) }} · " +
                        "${stringResource(R.string.pref_auto_rule_action).lowercase()} ${rule.actionIndex}",
                    Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.TextButton(
                    onClick = { preferencesRepository.automationManager.removeRule(rule.id) },
                    enabled = !BuildConfig.DEBUG
                ) { Text(stringResource(R.string.pref_auto_rule_remove)) }
            }
        }
    }
}
