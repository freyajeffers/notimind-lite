package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository

@Composable
fun debugSettingsSection(preferencesRepository: PreferencesRepository) {
    val retentionDays by preferencesRepository.retentionDays.collectAsState(initial = 30)
    val exportEncryption by preferencesRepository.exportEncryption.collectAsState(initial = true)
    val captureNotifications by preferencesRepository.captureNotifications.collectAsState(initial = true)
    val autoDeleteOnRead by preferencesRepository.autoDeleteOnRead.collectAsState(initial = false)
    val anonymizeTitles by preferencesRepository.anonymizeTitles.collectAsState(initial = false)

    if (BuildConfig.DEBUG) {
        LaunchedEffect(Unit) {
            preferencesRepository.setAutoDeleteOnRead(false)
            preferencesRepository.setAnonymizeTitles(false)
        }
        disabledPreferenceRow(
            title = stringResource(R.string.pref_auto_delete_on_read_title),
            summary = stringResource(R.string.pref_disabled_in_debug)
        )
        disabledPreferenceRow(
            title = stringResource(R.string.pref_anonymize_titles_title),
            summary = stringResource(R.string.pref_disabled_in_debug)
        )
        debugRetentionRow(retentionDays)
        debugCaptureRow()
    } else {
        preferenceSwitchRow(
            title = stringResource(R.string.pref_auto_delete_on_read_title),
            summary = stringResource(R.string.pref_auto_delete_on_read_summary),
            checked = autoDeleteOnRead,
            onCheckedChange = preferencesRepository::setAutoDeleteOnRead
        )
        preferenceSwitchRow(
            title = stringResource(R.string.pref_anonymize_titles_title),
            summary = stringResource(R.string.pref_anonymize_titles_summary),
            checked = anonymizeTitles,
            onCheckedChange = preferencesRepository::setAnonymizeTitles
        )
        Text(
            text = stringResource(R.string.pref_data_search_debug_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        retentionRow(preferencesRepository, retentionDays)
        exportEncryptionRow(preferencesRepository, exportEncryption)
        captureRow(preferencesRepository, captureNotifications)
    }
}

@Composable
private fun debugRetentionRow(retentionDays: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_retention_days_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_retention_days_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = retentionDays.toString(), onValueChange = {}, modifier = Modifier.width(120.dp),
            singleLine = true, enabled = false, label = { Text(stringResource(R.string.pref_retention_days_unit)) }
        )
    }
}

@Composable
private fun debugCaptureRow() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_capture_notifications_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_capture_notifications_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = true, onCheckedChange = {}, enabled = false)
    }
}

@Composable
private fun retentionRow(preferencesRepository: PreferencesRepository, retentionDays: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_retention_days_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_retention_days_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = retentionDays.toString(),
            onValueChange = { new -> new.toIntOrNull()?.let(preferencesRepository::setRetentionDays) },
            modifier = Modifier.width(120.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.pref_retention_days_unit)) },
            supportingText = { Text(stringResource(R.string.pref_retention_days_range)) }
        )
    }
}

@Composable
private fun exportEncryptionRow(preferencesRepository: PreferencesRepository, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_export_encryption_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_export_encryption_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = preferencesRepository::setExportEncryption)
    }
}

@Composable
private fun captureRow(preferencesRepository: PreferencesRepository, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_capture_notifications_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_capture_notifications_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = preferencesRepository::setCaptureNotifications)
    }
}
