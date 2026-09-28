package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R

@Composable
fun SettingsAdvancedSection(preferencesRepository: PreferencesRepository) {
    val enableTelemetry by preferencesRepository.enableTelemetry.collectAsState(initial = true)
    val telemetryLevel by preferencesRepository.telemetryLevel.collectAsState(initial = "minimal")
    val maxDbMb by preferencesRepository.maxDbMb.collectAsState(initial = 512)
    val lowMemoryMode by preferencesRepository.lowMemoryMode.collectAsState(initial = false)
    val vectorCacheMax by preferencesRepository.vectorCacheMax.collectAsState(initial = 1000)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_advanced_title), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_enable_telemetry_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_enable_telemetry_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableTelemetry, onCheckedChange = preferencesRepository::setEnableTelemetry)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_low_memory_mode_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_low_memory_mode_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = lowMemoryMode, onCheckedChange = preferencesRepository::setLowMemoryMode)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_max_db_mb_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_max_db_mb_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = maxDbMb.toString(), onValueChange = { it.toIntOrNull()?.let(preferencesRepository::setMaxDbMb) }, modifier = Modifier.width(120.dp), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_vector_cache_max_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_vector_cache_max_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = vectorCacheMax.toString(), onValueChange = { it.toIntOrNull()?.let(preferencesRepository::setVectorCacheMax) }, modifier = Modifier.width(120.dp), singleLine = true)
            }
        }
    }
}
