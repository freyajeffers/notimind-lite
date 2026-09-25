package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.local.PreferencesRepository


@Composable
fun SettingsPreferencesSection(preferencesRepository: PreferencesRepository) {

    val enableSync by preferencesRepository.enableSync.collectAsState(initial = true)
    val enableVector by preferencesRepository.enableVector.collectAsState(initial = true)
    val enableFts4 by preferencesRepository.enableFts4.collectAsState(initial = true)
    val retentionDays by preferencesRepository.retentionDays.collectAsState(initial = 30)
    val exportEncryption by preferencesRepository.exportEncryption.collectAsState(initial = true)
    val captureNotifications by preferencesRepository.captureNotifications.collectAsState(initial = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Data & Search", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Cloud Sync", style = MaterialTheme.typography.bodyLarge)
                    Text("Toggle automatic cloud sync and backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableSync, onCheckedChange = preferencesRepository::setEnableSync)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Semantic Vector Search", style = MaterialTheme.typography.bodyLarge)
                    Text("Enable semantic similarity search using vector embeddings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableVector, onCheckedChange = preferencesRepository::setEnableVector)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable FTS4 Keyword Search", style = MaterialTheme.typography.bodyLarge)
                    Text("Enable full-text keyword search using FTS4.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableFts4, onCheckedChange = preferencesRepository::setEnableFts4)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Retention (days)", style = MaterialTheme.typography.bodyLarge)
                    Text("How long to keep notifications before auto-deleting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = retentionDays.toString(), onValueChange = { new ->
                    new.toIntOrNull()?.let(preferencesRepository::setRetentionDays)
                }, modifier = Modifier.width(120.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Encrypt Exports", style = MaterialTheme.typography.bodyLarge)
                    Text("Require passphrase when exporting backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = exportEncryption, onCheckedChange = preferencesRepository::setExportEncryption)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Capture Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text("If disabled, the notification listener will not persist new notifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = captureNotifications, onCheckedChange = preferencesRepository::setCaptureNotifications)
            }
        }
    }
}
