package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.sync.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun SettingsSyncSection(preferencesRepository: PreferencesRepository, isAuthenticated: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val syncInterval by preferencesRepository.syncManager.syncInterval.collectAsState(initial = 60)
    val syncWifiOnly by preferencesRepository.syncManager.syncWifiOnly.collectAsState(initial = true)
    val syncChargingOnly by preferencesRepository.syncManager.syncChargingOnly.collectAsState(initial = false)
    val lastSyncTs by preferencesRepository.syncManager.lastSyncTs.collectAsState(initial = 0L)
    val useSupabaseSync by preferencesRepository.syncManager.useSupabaseSync.collectAsState(initial = false)
    val syncTitleAccessibleName = stringResource(R.string.pref_sync_title)
    var syncWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
    val syncWorkInfo by syncWorkId?.let {
        WorkManager.getInstance(context).getWorkInfoByIdFlow(it).collectAsState(initial = null)
    } ?: remember { mutableStateOf(null) }
    val syncRunning = syncWorkInfo?.state == WorkInfo.State.RUNNING ||
        syncWorkInfo?.state == WorkInfo.State.ENQUEUED

    LaunchedEffect(syncWorkInfo?.state) {
        if (syncWorkInfo?.state == WorkInfo.State.SUCCEEDED) {
            preferencesRepository.syncManager.refreshLastSyncTs()
        }
    }
    val syncStatusMessage = when (syncWorkInfo?.state) {
        WorkInfo.State.SUCCEEDED -> stringResource(
            R.string.settings_sync_success,
            syncWorkInfo?.outputData?.getInt(SyncWorker.OUTPUT_SYNC_COUNT, 0) ?: 0
        )

        WorkInfo.State.FAILED -> stringResource(R.string.settings_sync_failure)

        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_section_sync),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    contentDescription = syncTitleAccessibleName
                }
            )
            Text(
                text = stringResource(
                    if (isAuthenticated) {
                        R.string.settings_sync_idle
                    } else {
                        R.string.settings_sync_offline
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (syncStatusMessage != null) {
                Text(
                    text = syncStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncWorkInfo?.state == WorkInfo.State.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            SyncIntervalRow(
                syncInterval = syncInterval,
                onChange = { value ->
                    scope.launch { preferencesRepository.syncManager.setSyncInterval(value) }
                }
            )

            SyncToggleRow(
                title = stringResource(R.string.pref_sync_wifi_title),
                summary = stringResource(R.string.pref_sync_wifi_summary),
                checked = syncWifiOnly,
                onChecked = { scope.launch { preferencesRepository.syncManager.setSyncWifiOnly(it) } }
            )

            SyncToggleRow(
                title = stringResource(R.string.pref_sync_charging_title),
                summary = stringResource(R.string.pref_sync_charging_summary),
                checked = syncChargingOnly,
                onChecked = { scope.launch { preferencesRepository.syncManager.setSyncChargingOnly(it) } }
            )

            SyncToggleRow(
                title = "Supabase cloud sync (default)",
                summary = "Uses the existing Supabase project; disable to use Firebase sync",
                checked = useSupabaseSync,
                onChecked = { scope.launch { preferencesRepository.syncManager.setUseSupabaseSync(it) } }
            )

            SyncNowRow(
                lastSyncTs = lastSyncTs,
                syncRunning = syncRunning,
                onClick = { syncWorkId = SyncWorker.runImmediateSync(context) }
            )
        }
    }
}

@Composable
private fun SyncIntervalRow(syncInterval: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.pref_sync_interval_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pref_sync_interval_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = syncInterval.toString(),
            onValueChange = { it.toIntOrNull()?.let(onChange) },
            modifier = Modifier.width(120.dp),
            singleLine = true
        )
    }
}

@Composable
private fun SyncToggleRow(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SyncNowRow(lastSyncTs: Long, syncRunning: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        val lastSyncText = if (lastSyncTs == 0L) {
            stringResource(R.string.pref_sync_never)
        } else {
            java.text.DateFormat
                .getDateTimeInstance()
                .format(java.util.Date(lastSyncTs))
        }
        Text(
            text = stringResource(R.string.pref_sync_last, lastSyncText),
            style = MaterialTheme.typography.bodySmall
        )
        val syncButtonDescription = stringResource(
            if (syncRunning) R.string.settings_sync_now_loading else R.string.pref_sync_now
        )
        Button(
            onClick = onClick,
            enabled = !syncRunning,
            modifier = Modifier.semantics { contentDescription = syncButtonDescription }
        ) {
            if (syncRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(
                    if (syncRunning) {
                        R.string.settings_sync_now_loading
                    } else {
                        R.string.settings_sync_now_idle
                    }
                )
            )
        }
    }
}
