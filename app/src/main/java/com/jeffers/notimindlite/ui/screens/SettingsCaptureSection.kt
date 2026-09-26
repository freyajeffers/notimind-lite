package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun SettingsCaptureSection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val captureEnabled by preferencesRepository.captureNotifications.collectAsState(initial = true)
    val foregroundOnly by preferencesRepository.captureForegroundOnly.collectAsState(initial = false)
    val attachments by preferencesRepository.captureAttachments.collectAsState(initial = true)
    val ongoing by preferencesRepository.captureOngoing.collectAsState(initial = true)
    val allowlist by preferencesRepository.capturePackageAllowlist.collectAsState(initial = "")
    val blocklist by preferencesRepository.capturePackageBlocklist.collectAsState(initial = "")
    val minImportance by preferencesRepository.minImportance.collectAsState(initial = 0)
    val actionsOnly by preferencesRepository.captureActionsOnly.collectAsState(initial = false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_capture_title), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_enabled_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_enabled_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = captureEnabled, onCheckedChange = { scope.launch { preferencesRepository.setCaptureNotifications(it) } }, enabled = !BuildConfig.DEBUG)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_foreground_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_foreground_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = foregroundOnly, onCheckedChange = { scope.launch { preferencesRepository.setCaptureForegroundOnly(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_attachments_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_attachments_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = attachments, onCheckedChange = { scope.launch { preferencesRepository.setCaptureAttachments(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_ongoing_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_ongoing_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ongoing, onCheckedChange = { scope.launch { preferencesRepository.setCaptureOngoing(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_allowlist_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_allowlist_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = allowlist, onValueChange = { scope.launch { preferencesRepository.setCapturePackageAllowlist(it) } }, modifier = Modifier.width(200.dp), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_blocklist_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_blocklist_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = blocklist, onValueChange = { scope.launch { preferencesRepository.setCapturePackageBlocklist(it) } }, modifier = Modifier.width(200.dp), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_min_importance_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_min_importance_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = minImportance.toString(), onValueChange = { it.toIntOrNull()?.let { v -> scope.launch { preferencesRepository.setMinImportance(v) } } }, modifier = Modifier.width(120.dp), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_capture_actions_only_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_capture_actions_only_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = actionsOnly, onCheckedChange = { scope.launch { preferencesRepository.setCaptureActionsOnly(it) } })
            }

        }
    }
}
