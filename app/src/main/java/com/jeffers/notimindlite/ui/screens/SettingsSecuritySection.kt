package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsSecuritySection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val encryptedExports by preferencesRepository.securityManager.encryptedExports.collectAsState(initial = true)
    val requirePassphrase by preferencesRepository.securityManager.requirePassphrase.collectAsState(initial = true)
    val autoLockDb by preferencesRepository.securityManager.autoLockDb.collectAsState(initial = false)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_security_title),
                style = MaterialTheme.typography.titleMedium
            )
            securityToggleRow(
                title = stringResource(R.string.pref_encrypted_exports_title),
                summary = stringResource(R.string.pref_encrypted_exports_summary),
                checked = encryptedExports,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setEncryptedExports(value) }
                }
            )
            securityToggleRow(
                title = stringResource(R.string.pref_require_passphrase_title),
                summary = stringResource(R.string.pref_require_passphrase_summary),
                checked = requirePassphrase,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setRequirePassphrase(value) }
                }
            )
            securityToggleRow(
                title = stringResource(R.string.pref_auto_lock_title),
                summary = stringResource(R.string.pref_auto_lock_summary),
                checked = autoLockDb,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setAutoLockDb(value) }
                }
            )
        }
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
