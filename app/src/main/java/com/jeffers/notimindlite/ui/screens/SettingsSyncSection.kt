package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun SettingsSyncSection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val syncInterval by preferencesRepository.syncInterval.collectAsState(initial = 60)
    val syncWifiOnly by preferencesRepository.syncWifiOnly.collectAsState(initial = true)
    val syncChargingOnly by preferencesRepository.syncChargingOnly.collectAsState(initial = false)
    val lastSyncTs by preferencesRepository.lastSyncTs.collectAsState(initial = 0L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_sync_title), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_sync_interval_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_sync_interval_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = syncInterval.toString(), onValueChange = { it.toIntOrNull()?.let { v -> scope.launch { preferencesRepository.setSyncInterval(v) } } }, modifier = Modifier.width(120.dp), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_sync_wifi_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_sync_wifi_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = syncWifiOnly, onCheckedChange = { scope.launch { preferencesRepository.setSyncWifiOnly(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_sync_charging_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_sync_charging_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = syncChargingOnly, onCheckedChange = { scope.launch { preferencesRepository.setSyncChargingOnly(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(text = stringResource(R.string.pref_sync_last, if (lastSyncTs == 0L) stringResource(R.string.pref_sync_never) else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSyncTs))), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { scope.launch { preferencesRepository.setLastSyncTs(System.currentTimeMillis()) /* placeholder: trigger SyncWorker externally */ } }) {
                    Text(stringResource(R.string.pref_sync_now))
                }
            }
        }
    }
}
