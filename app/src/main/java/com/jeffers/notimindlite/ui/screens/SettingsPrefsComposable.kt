package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun SettingsPreferencesSection(preferencesRepository: PreferencesRepository) {

    val enableSync by preferencesRepository.enableSync.collectAsState(initial = true)
    val enableVector by preferencesRepository.enableVector.collectAsState(initial = true)
    val enableFts4 by preferencesRepository.enableFts4.collectAsState(initial = true)
    val retentionDays by preferencesRepository.retentionDays.collectAsState(initial = 30)
    val exportEncryption by preferencesRepository.exportEncryption.collectAsState(initial = true)
    val captureNotifications by preferencesRepository.captureNotifications.collectAsState(initial = true)
    val enableSemanticRanking by preferencesRepository.enableSemanticRanking.collectAsState(initial = true)
    val autoDeleteOnRead by preferencesRepository.autoDeleteOnRead.collectAsState(initial = false)
    val backupIntervalDays by preferencesRepository.backupIntervalDays.collectAsState(initial = 0)
    val anonymizeTitles by preferencesRepository.anonymizeTitles.collectAsState(initial = false)
    val maxCacheSizeMb by preferencesRepository.maxCacheSizeMb.collectAsState(initial = 50)
    val semanticWeight by preferencesRepository.semanticWeight.collectAsState(initial = 50)
    val compactMode by preferencesRepository.compactMode.collectAsState(initial = false)
    val showAppIcons by preferencesRepository.showAppIcons.collectAsState(initial = true)
    val groupByApp by preferencesRepository.groupByApp.collectAsState(initial = true)
    val sortOrder by preferencesRepository.sortOrder.collectAsState(initial = "newest")
    val previewLength by preferencesRepository.previewLength.collectAsState(initial = 140)
    val themeAccent by preferencesRepository.themeAccent.collectAsState(initial = "system")
    val exportRequiresBiometric by preferencesRepository.exportRequiresBiometric.collectAsState(initial = false)
    val appLockEnabled by preferencesRepository.appLockEnabled.collectAsState(initial = false)
    val useKeystore by preferencesRepository.useKeystore.collectAsState(initial = true)
    val dbEncrypted by preferencesRepository.dbEncrypted.collectAsState(initial = true)
    val dbEncryptionMode by preferencesRepository.dbEncryptionMode.collectAsState(initial = DbEncryptionMode.KEYSTORE)
    val autoActOnNotification by preferencesRepository.autoActOnNotification.collectAsState(initial = false)
    val defaultReplyMethod by preferencesRepository.defaultReplyMethod.collectAsState(initial = "inline")
    val longPressAction by preferencesRepository.longPressAction.collectAsState(initial = "open")
    val autoExecuteRules by preferencesRepository.autoExecuteRules.collectAsState(initial = emptyList())
    var rulePackage by remember { mutableStateOf("") }
    var ruleTitle by remember { mutableStateOf("") }
    var ruleAction by remember { mutableStateOf("0") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_data_search_title), style = MaterialTheme.typography.titleMedium)

            Text(stringResource(R.string.pref_display_title), style = MaterialTheme.typography.titleMedium)
            PreferenceSwitchRow(stringResource(R.string.pref_compact_mode_title), stringResource(R.string.pref_compact_mode_summary), compactMode, preferencesRepository::setCompactMode)
            PreferenceSwitchRow(stringResource(R.string.pref_show_app_icons_title), stringResource(R.string.pref_show_app_icons_summary), showAppIcons, preferencesRepository::setShowAppIcons)
            PreferenceSwitchRow(stringResource(R.string.pref_group_by_app_title), stringResource(R.string.pref_group_by_app_summary), groupByApp, preferencesRepository::setGroupByApp)
            PreferenceSelectRow(stringResource(R.string.pref_sort_order_title), sortOrder, listOf("newest" to stringResource(R.string.pref_sort_newest), "oldest" to stringResource(R.string.pref_sort_oldest), "app" to stringResource(R.string.pref_sort_app)), preferencesRepository::setSortOrder)
            PreferenceSelectRow(stringResource(R.string.pref_theme_accent_title), themeAccent, listOf("system" to stringResource(R.string.pref_theme_system), "blue" to stringResource(R.string.pref_theme_blue), "green" to stringResource(R.string.pref_theme_green), "purple" to stringResource(R.string.pref_theme_purple), "orange" to stringResource(R.string.pref_theme_orange)), preferencesRepository::setThemeAccent)

            Text(stringResource(R.string.pref_automation_title), style = MaterialTheme.typography.titleMedium)
            PreferenceSwitchRow(stringResource(R.string.pref_auto_act_title), stringResource(R.string.pref_auto_act_summary), autoActOnNotification, preferencesRepository::setAutoActOnNotification)
            PreferenceSelectRow(stringResource(R.string.pref_default_reply_title), defaultReplyMethod, listOf("inline" to stringResource(R.string.pref_reply_inline), "open_app" to stringResource(R.string.pref_reply_open_app), "copy" to stringResource(R.string.pref_reply_copy)), preferencesRepository::setDefaultReplyMethod)
            PreferenceSelectRow(stringResource(R.string.pref_long_press_title), longPressAction, listOf("open" to stringResource(R.string.pref_action_open), "reply" to stringResource(R.string.pref_action_reply), "dismiss" to stringResource(R.string.pref_action_dismiss)), preferencesRepository::setLongPressAction)
            Text(stringResource(R.string.pref_auto_rules_title), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(rulePackage, { rulePackage = it }, Modifier.weight(1f).semantics { contentDescription = stringResource(R.string.pref_auto_rule_package) }, singleLine = true, label = { Text(stringResource(R.string.pref_auto_rule_package)) })
                OutlinedTextField(ruleTitle, { ruleTitle = it }, Modifier.weight(1f).semantics { contentDescription = stringResource(R.string.pref_auto_rule_title) }, singleLine = true, label = { Text(stringResource(R.string.pref_auto_rule_title)) })
                OutlinedTextField(ruleAction, { ruleAction = it }, Modifier.width(72.dp).semantics { contentDescription = stringResource(R.string.pref_auto_rule_action) }, singleLine = true, label = { Text(stringResource(R.string.pref_auto_rule_action)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Button(onClick = {
                preferencesRepository.addAutoExecuteRule(AutoExecuteRule(packageName = rulePackage.trim(), titleContains = ruleTitle.trim(), actionIndex = ruleAction.toIntOrNull()?.coerceAtLeast(0) ?: 0))
                rulePackage = ""; ruleTitle = ""; ruleAction = "0"
            }, modifier = Modifier.semantics { contentDescription = stringResource(R.string.pref_auto_rule_add) }) { Text(stringResource(R.string.pref_auto_rule_add)) }
            autoExecuteRules.forEach { rule ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${rule.packageName.ifBlank { stringResource(R.string.pref_auto_rule_any_app) }} · ${rule.titleContains.ifBlank { stringResource(R.string.pref_auto_rule_any_title) }} · ${stringResource(R.string.pref_auto_rule_action).lowercase()} ${rule.actionIndex}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { preferencesRepository.removeAutoExecuteRule(rule.id) }, modifier = Modifier.semantics { contentDescription = stringResource(R.string.pref_auto_rule_remove) }) { Text(stringResource(R.string.pref_auto_rule_remove)) }
                }
            }

            Text(stringResource(R.string.pref_security_local_title), style = MaterialTheme.typography.titleMedium)
            PreferenceSwitchRow(stringResource(R.string.pref_export_biometric_title), stringResource(R.string.pref_export_biometric_summary), exportRequiresBiometric, preferencesRepository::setExportRequiresBiometric)
            PreferenceSwitchRow(stringResource(R.string.pref_app_lock_title), stringResource(R.string.pref_app_lock_summary), appLockEnabled, preferencesRepository::setAppLockEnabled)
            PreferenceSwitchRow(stringResource(R.string.pref_encrypt_database_title), stringResource(R.string.pref_encrypt_database_summary), dbEncrypted, preferencesRepository::setDbEncrypted)
            PreferenceSelectRow(stringResource(R.string.pref_database_key_mode_title), dbEncryptionMode.persisted, listOf("none" to stringResource(R.string.pref_key_none), "keystore" to stringResource(R.string.pref_key_keystore), "local_key" to stringResource(R.string.pref_key_local)), { preferencesRepository.setDbEncryptionMode(DbEncryptionMode.fromPersisted(it)) })
            PreferenceSwitchRow(stringResource(R.string.pref_keystore_title), stringResource(R.string.pref_keystore_summary), useKeystore, preferencesRepository::setUseKeystore, enabled = dbEncrypted)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_preview_length_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_preview_length_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = previewLength.toString(), onValueChange = { it.toIntOrNull()?.let(preferencesRepository::setPreviewLength) }, modifier = Modifier.width(110.dp), singleLine = true, label = { Text(stringResource(R.string.pref_preview_length_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_sync_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_enable_sync_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableSync, onCheckedChange = preferencesRepository::setEnableSync)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_vector_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_enable_vector_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableVector, onCheckedChange = preferencesRepository::setEnableVector)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_fts4_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_enable_fts4_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableFts4, onCheckedChange = preferencesRepository::setEnableFts4)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_semantic_ranking_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_semantic_ranking_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableSemanticRanking, onCheckedChange = preferencesRepository::setEnableSemanticRanking)
            }

            if (BuildConfig.DEBUG) {
                LaunchedEffect(Unit) {
                    preferencesRepository.setAutoDeleteOnRead(false)
                    preferencesRepository.setAnonymizeTitles(false)
                }
                DisabledPreferenceRow(
                    title = stringResource(R.string.pref_auto_delete_on_read_title),
                    summary = stringResource(R.string.pref_disabled_in_debug)
                )
                DisabledPreferenceRow(
                    title = stringResource(R.string.pref_anonymize_titles_title),
                    summary = stringResource(R.string.pref_disabled_in_debug)
                )

                // Show debug-effective controls while preventing changes to them.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_retention_days_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pref_retention_days_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = retentionDays.toString(),
                        onValueChange = { /* disabled in debug */ },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.pref_retention_days_unit)) }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_capture_notifications_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pref_capture_notifications_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = true, onCheckedChange = { /* disabled in debug */ }, enabled = false)
                }
            } else {
                PreferenceSwitchRow(
                    title = stringResource(R.string.pref_auto_delete_on_read_title),
                    summary = stringResource(R.string.pref_auto_delete_on_read_summary),
                    checked = autoDeleteOnRead,
                    onCheckedChange = preferencesRepository::setAutoDeleteOnRead
                )
                PreferenceSwitchRow(
                    title = stringResource(R.string.pref_anonymize_titles_title),
                    summary = stringResource(R.string.pref_anonymize_titles_summary),
                    checked = anonymizeTitles,
                    onCheckedChange = preferencesRepository::setAnonymizeTitles
                )
            }

            if (BuildConfig.DEBUG) {
                Text(
                    text = stringResource(R.string.pref_data_search_debug_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_retention_days_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pref_retention_days_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = retentionDays.toString(),
                        onValueChange = { new -> new.toIntOrNull()?.let(preferencesRepository::setRetentionDays) },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.pref_retention_days_unit)) },
                        supportingText = { Text(stringResource(R.string.pref_retention_days_range)) }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_export_encryption_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pref_export_encryption_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = exportEncryption, onCheckedChange = preferencesRepository::setExportEncryption)
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pref_capture_notifications_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.pref_capture_notifications_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = captureNotifications, onCheckedChange = preferencesRepository::setCaptureNotifications)
                }
            }
        }
    }
}


@Composable
private fun DisabledPreferenceRow(title: String, summary: String) {
    PreferenceSwitchRow(title, summary, checked = false, onCheckedChange = {}, enabled = false)
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun PreferenceSelectRow(title: String, selected: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = title }) { Text(options.firstOrNull { it.first == selected }?.second ?: selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(key); expanded = false })
                }
            }
        }
    }
}
