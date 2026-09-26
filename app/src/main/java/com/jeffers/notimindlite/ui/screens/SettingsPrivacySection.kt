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
fun SettingsPrivacySection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val autoDelete by preferencesRepository.autoDeleteOnRead.collectAsState(initial = false)
    val anonymizeTitles by preferencesRepository.anonymizeTitles.collectAsState(initial = false)
    val redactPii by preferencesRepository.redactPii.collectAsState(initial = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_privacy_title), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_auto_delete_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_auto_delete_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoDelete, 
                    onCheckedChange = { scope.launch { preferencesRepository.setAutoDeleteOnRead(it) } },
                    enabled = !BuildConfig.DEBUG
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_anonymize_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_anonymize_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = anonymizeTitles, 
                    onCheckedChange = { scope.launch { preferencesRepository.setAnonymizeTitles(it) } },
                    enabled = !BuildConfig.DEBUG
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_redact_pii_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_redact_pii_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = redactPii, onCheckedChange = { scope.launch { preferencesRepository.setRedactPii(it) } })
            }
        }
    }
}
