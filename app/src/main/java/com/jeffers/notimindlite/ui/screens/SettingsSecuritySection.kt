package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.R
import kotlinx.coroutines.launch

@Composable
fun SettingsSecuritySection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val encryptedExports by preferencesRepository.encryptedExports.collectAsState(initial = true)
    val requirePassphrase by preferencesRepository.requirePassphrase.collectAsState(initial = true)
    val autoLockDb by preferencesRepository.autoLockDb.collectAsState(initial = false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.pref_security_title), style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_encrypted_exports_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_encrypted_exports_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = encryptedExports, onCheckedChange = { scope.launch { preferencesRepository.setEncryptedExports(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_require_passphrase_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_require_passphrase_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = requirePassphrase, onCheckedChange = { scope.launch { preferencesRepository.setRequirePassphrase(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_auto_lock_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_auto_lock_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoLockDb, onCheckedChange = { scope.launch { preferencesRepository.setAutoLockDb(it) } })
            }
        }
    }
}
