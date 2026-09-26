package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.data.local.PreferencesRepository
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_data_search_title), style = MaterialTheme.typography.titleMedium)

            Text("Display & appearance", style = MaterialTheme.typography.titleMedium)
            PreferenceSwitchRow("Compact mode", "Use denser notification cards.", compactMode, preferencesRepository::setCompactMode)
            PreferenceSwitchRow("Show app icons", "Show application icons in notification cards.", showAppIcons, preferencesRepository::setShowAppIcons)
            PreferenceSwitchRow("Group by app", "Combine notifications from the same application.", groupByApp, preferencesRepository::setGroupByApp)
            PreferenceSelectRow("Sort order", sortOrder, listOf("newest", "oldest", "app"), preferencesRepository::setSortOrder)
            PreferenceSelectRow("Theme accent", themeAccent, listOf("system", "blue", "green", "purple", "orange"), preferencesRepository::setThemeAccent)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preview length", style = MaterialTheme.typography.bodyLarge)
                    Text("Maximum characters shown before expanding.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = previewLength.toString(), onValueChange = { it.toIntOrNull()?.let(preferencesRepository::setPreviewLength) }, modifier = Modifier.width(110.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
                    Text("Semantic ranking", style = MaterialTheme.typography.bodyLarge)
                    Text("Blend semantic similarity into search ranking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PreferenceSelectRow(title: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selected.replaceFirstChar { it.uppercase() }) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.replaceFirstChar { it.uppercase() }) }, onClick = { onSelected(option); expanded = false })
                }
            }
        }
    }
}
