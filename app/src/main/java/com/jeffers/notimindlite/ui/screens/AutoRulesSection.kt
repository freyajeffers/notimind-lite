package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.jeffers.notimindlite.data.local.AutoExecuteRule
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R

@Composable
fun autoRulesSection(
    preferencesRepository: PreferencesRepository,
    autoExecuteRules: List<AutoExecuteRule>,
    rulePackage: String,
    onRulePackageChange: (String) -> Unit,
    ruleTitle: String,
    onRuleTitleChange: (String) -> Unit,
    ruleAction: String,
    onRuleActionChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                rulePackage, onValueChange = onRulePackageChange, Modifier.weight(1f), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_package)) }
            )
            OutlinedTextField(
                ruleTitle, onValueChange = onRuleTitleChange, Modifier.weight(1f), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_title)) }
            )
            OutlinedTextField(
                ruleAction, onValueChange = onRuleActionChange, Modifier.width(72.dp), singleLine = true,
                enabled = !BuildConfig.DEBUG, label = { Text(stringResource(R.string.pref_auto_rule_action)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Button(onClick = {
            preferencesRepository.addAutoExecuteRule(
                AutoExecuteRule(
                    packageName = rulePackage.trim(), titleContains = ruleTitle.trim(),
                    actionIndex = ruleAction.toIntOrNull()?.coerceAtLeast(0) ?: 0
                )
            )
            onRulePackageChange(""); onRuleTitleChange(""); onRuleActionChange("0")
        }, enabled = !BuildConfig.DEBUG) { Text(stringResource(R.string.pref_auto_rule_add)) }

        autoExecuteRules.forEach { rule ->
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
                    onClick = { preferencesRepository.removeAutoExecuteRule(rule.id) },
                    enabled = !BuildConfig.DEBUG
                ) { Text(stringResource(R.string.pref_auto_rule_remove)) }
            }
        }
    }
}
