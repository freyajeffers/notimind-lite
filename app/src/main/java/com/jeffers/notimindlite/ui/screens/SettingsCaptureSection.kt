package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun settingsCaptureSection(preferencesRepository: PreferencesRepository) {
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

            captureToggleRow(
                title = stringResource(R.string.pref_capture_enabled_title),
                summary = stringResource(R.string.pref_capture_enabled_summary),
                checked = captureEnabled,
                onCheckedChange = { scope.launch { preferencesRepository.setCaptureNotifications(it) } },
                enabled = !BuildConfig.DEBUG
            )

            captureToggleRow(
                title = stringResource(R.string.pref_capture_foreground_title),
                summary = stringResource(R.string.pref_capture_foreground_summary),
                checked = foregroundOnly,
                onCheckedChange = { scope.launch { preferencesRepository.setCaptureForegroundOnly(it) } },
                enabled = !BuildConfig.DEBUG
            )

            captureToggleRow(
                title = stringResource(R.string.pref_capture_attachments_title),
                summary = stringResource(R.string.pref_capture_attachments_summary),
                checked = attachments,
                onCheckedChange = { scope.launch { preferencesRepository.setCaptureAttachments(it) } },
                enabled = !BuildConfig.DEBUG
            )

            captureToggleRow(
                title = stringResource(R.string.pref_capture_ongoing_title),
                summary = stringResource(R.string.pref_capture_ongoing_summary),
                checked = ongoing,
                onCheckedChange = { scope.launch { preferencesRepository.setCaptureOngoing(it) } },
                enabled = !BuildConfig.DEBUG
            )

            captureTextFieldRow(
                title = stringResource(R.string.pref_capture_allowlist_title),
                summary = stringResource(R.string.pref_capture_allowlist_summary),
                value = allowlist,
                onValueChange = { scope.launch { preferencesRepository.setCapturePackageAllowlist(it) } },
                width = 200.dp,
                enabled = !BuildConfig.DEBUG
            )

            captureTextFieldRow(
                title = stringResource(R.string.pref_capture_blocklist_title),
                summary = stringResource(R.string.pref_capture_blocklist_summary),
                value = blocklist,
                onValueChange = { scope.launch { preferencesRepository.setCapturePackageBlocklist(it) } },
                width = 200.dp,
                enabled = !BuildConfig.DEBUG
            )

            captureTextFieldRow(
                title = stringResource(R.string.pref_capture_min_importance_title),
                summary = stringResource(R.string.pref_capture_min_importance_summary),
                value = minImportance.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> scope.launch { preferencesRepository.setMinImportance(v) } } },
                width = 120.dp,
                enabled = !BuildConfig.DEBUG
            )

            captureToggleRow(
                title = stringResource(R.string.pref_capture_actions_only_title),
                summary = stringResource(R.string.pref_capture_actions_only_summary),
                checked = actionsOnly,
                onCheckedChange = { scope.launch { preferencesRepository.setCaptureActionsOnly(it) } },
                enabled = !BuildConfig.DEBUG
            )

        }
    }
}

@Composable
private fun captureToggleRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun captureTextFieldRow(title: String, summary: String, value: String, onValueChange: (String) -> Unit, width: androidx.compose.ui.unit.Dp, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.width(width), singleLine = true, enabled = enabled)
    }
}
