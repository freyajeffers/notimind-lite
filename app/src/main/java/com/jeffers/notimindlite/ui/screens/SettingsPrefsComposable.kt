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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_data_search_title), style = MaterialTheme.typography.titleMedium)

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
