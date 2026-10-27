package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R

internal data class PreferencesBackupCardConfig(
    val exportFormats: String,
    val exportAnonymize: Boolean,
    val onFormatsChange: (String) -> Unit,
    val onAnonymizeChange: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val message: String?,
    val enabled: Boolean
)

@Composable
internal fun PreferencesBackupCard(config: PreferencesBackupCardConfig) {
    val exportFormats = config.exportFormats
    val exportAnonymize = config.exportAnonymize
    val onFormatsChange = config.onFormatsChange
    val onAnonymizeChange = config.onAnonymizeChange
    val onExport = config.onExport
    val onImport = config.onImport
    val message = config.message
    val enabled = config.enabled
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pref_backup_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pref_backup_settings_summary), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = exportFormats,
                onValueChange = onFormatsChange,
                label = { Text(stringResource(R.string.pref_export_formats)) },
                supportingText = { Text(stringResource(R.string.pref_export_formats_summary)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.pref_export_anonymize),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.pref_export_anonymize_summary),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = exportAnonymize, onCheckedChange = onAnonymizeChange, enabled = enabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = enabled) {
                    Text(stringResource(R.string.pref_backup_export))
                }
                OutlinedButton(onClick = onImport, enabled = enabled) {
                    Text(stringResource(R.string.pref_backup_import))
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
