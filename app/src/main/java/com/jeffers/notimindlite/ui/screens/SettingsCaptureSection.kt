package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun settingsCaptureSection(preferencesRepository: PreferencesRepository) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_capture_title), style = MaterialTheme.typography.titleMedium)

            captureToggleSection(preferencesRepository = preferencesRepository)

            captureTextFieldSection(preferencesRepository = preferencesRepository)

        }
    }
}

@Composable
private fun captureToggleSection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val captureEnabled by preferencesRepository.captureManager.enabled.collectAsState(initial = true)
    val foregroundOnly by preferencesRepository.captureManager.foregroundOnly.collectAsState(initial = false)
    val attachments by preferencesRepository.captureManager.attachments.collectAsState(initial = true)
    val ongoing by preferencesRepository.captureManager.ongoing.collectAsState(initial = true)
    val actionsOnly by preferencesRepository.captureManager.actionsOnly.collectAsState(initial = false)

    captureToggleRow(stringResource(R.string.pref_capture_enabled_title), stringResource(R.string.pref_capture_enabled_summary), captureEnabled, { scope.launch { preferencesRepository.captureManager.setEnabled(it) } }, !BuildConfig.DEBUG)
    captureToggleRow(stringResource(R.string.pref_capture_foreground_title), stringResource(R.string.pref_capture_foreground_summary), foregroundOnly, { scope.launch { preferencesRepository.captureManager.setForegroundOnly(it) } }, !BuildConfig.DEBUG)
    captureToggleRow(stringResource(R.string.pref_capture_attachments_title), stringResource(R.string.pref_capture_attachments_summary), attachments, { scope.launch { preferencesRepository.captureManager.setAttachments(it) } }, !BuildConfig.DEBUG)
    captureToggleRow(stringResource(R.string.pref_capture_ongoing_title), stringResource(R.string.pref_capture_ongoing_summary), ongoing, { scope.launch { preferencesRepository.captureManager.setOngoing(it) } }, !BuildConfig.DEBUG)
    captureToggleRow(stringResource(R.string.pref_capture_actions_only_title), stringResource(R.string.pref_capture_actions_only_summary), actionsOnly, { scope.launch { preferencesRepository.captureManager.setActionsOnly(it) } }, !BuildConfig.DEBUG)
}

@Composable
private fun captureTextFieldSection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val allowlist by preferencesRepository.captureManager.allowlist.collectAsState(initial = "")
    val blocklist by preferencesRepository.captureManager.blocklist.collectAsState(initial = "")
    val minImportance by preferencesRepository.captureManager.minImportance.collectAsState(initial = 0)

    captureTextFieldRow(CaptureTextFieldConfig(stringResource(R.string.pref_capture_allowlist_title), stringResource(R.string.pref_capture_allowlist_summary), allowlist, { scope.launch { preferencesRepository.captureManager.setAllowlist(it) } }, 200.dp, !BuildConfig.DEBUG))
    captureTextFieldRow(CaptureTextFieldConfig(stringResource(R.string.pref_capture_blocklist_title), stringResource(R.string.pref_capture_blocklist_summary), blocklist, { scope.launch { preferencesRepository.captureManager.setBlocklist(it) } }, 200.dp, !BuildConfig.DEBUG))
    captureTextFieldRow(CaptureTextFieldConfig(stringResource(R.string.pref_capture_min_importance_title), stringResource(R.string.pref_capture_min_importance_summary), minImportance.toString(), { it.toIntOrNull()?.let { v -> scope.launch { preferencesRepository.captureManager.setMinImportance(v) } } }, 120.dp, !BuildConfig.DEBUG))
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

private data class CaptureTextFieldConfig(
    val title: String,
    val summary: String,
    val value: String,
    val onValueChange: (String) -> Unit,
    val width: Dp,
    val enabled: Boolean = true
)

@Composable
private fun captureTextFieldRow(config: CaptureTextFieldConfig) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(config.title, style = MaterialTheme.typography.bodyLarge)
            Text(config.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = config.value,
            onValueChange = config.onValueChange,
            modifier = Modifier.width(config.width),
            singleLine = true,
            enabled = config.enabled
        )
    }
}
