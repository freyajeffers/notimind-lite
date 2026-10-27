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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.DbEncryptionMode
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsSecuritySection(preferencesRepository: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val encryptedExports by preferencesRepository.securityManager.encryptedExports.collectAsState(initial = true)
    val requirePassphrase by preferencesRepository.securityManager.requirePassphrase.collectAsState(initial = true)
    val autoLockDb by preferencesRepository.securityManager.autoLockDb.collectAsState(initial = false)
    val biometricExport by preferencesRepository.securityManager.exportRequiresBiometric.collectAsState(initial = false)
    val appLock by preferencesRepository.securityManager.appLockEnabled.collectAsState(initial = false)
    val databaseEncrypted by preferencesRepository.securityManager.dbEncrypted.collectAsState(initial = true)
    val useKeystore by preferencesRepository.securityManager.useKeystore.collectAsState(initial = true)
    val keyMode by preferencesRepository.securityManager.dbEncryptionMode.collectAsState(
        initial = DbEncryptionMode.KEYSTORE
    )
    val securityAccessibilityTitle = stringResource(R.string.pref_security_title)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_security_local_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = securityAccessibilityTitle }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_encrypted_exports_title),
                summary = stringResource(R.string.pref_encrypted_exports_summary),
                checked = encryptedExports,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setEncryptedExports(value) }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_require_passphrase_title),
                summary = stringResource(R.string.pref_require_passphrase_summary),
                checked = requirePassphrase,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setRequirePassphrase(value) }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_auto_lock_title),
                summary = stringResource(R.string.pref_auto_lock_summary),
                checked = autoLockDb,
                onCheckedChange = { value ->
                    scope.launch { preferencesRepository.securityManager.setAutoLockDb(value) }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_export_biometric_title),
                summary = stringResource(R.string.pref_export_biometric_summary),
                checked = biometricExport,
                onCheckedChange = { value ->
                    scope.launch {
                        preferencesRepository.securityManager.setExportRequiresBiometric(value)
                    }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_app_lock_title),
                summary = stringResource(R.string.pref_app_lock_summary),
                checked = appLock,
                onCheckedChange = { value ->
                    scope.launch {
                        preferencesRepository.securityManager.setAppLockEnabled(value)
                    }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_encrypt_database_title),
                summary = stringResource(R.string.pref_encrypt_database_summary),
                checked = databaseEncrypted,
                onCheckedChange = { value ->
                    scope.launch {
                        preferencesRepository.securityManager.setDbEncrypted(value)
                    }
                }
            )
            SecurityToggleRow(
                title = stringResource(R.string.pref_keystore_title),
                summary = stringResource(R.string.pref_keystore_summary),
                checked = useKeystore,
                onCheckedChange = { value ->
                    scope.launch {
                        preferencesRepository.securityManager.setUseKeystore(value)
                    }
                }
            )
            PreferenceSelectRow(
                title = stringResource(R.string.pref_database_key_mode_title),
                selected = keyMode.persisted,
                options = listOf(
                    DbEncryptionMode.NONE.persisted to stringResource(R.string.pref_key_none),
                    DbEncryptionMode.KEYSTORE.persisted to stringResource(R.string.pref_key_keystore),
                    DbEncryptionMode.LOCAL_KEY.persisted to stringResource(R.string.pref_key_local)
                ),
                onSelected = { value ->
                    DbEncryptionMode.entries.firstOrNull { it.persisted == value }?.let {
                        preferencesRepository.securityManager.setDbEncryptionMode(it)
                    }
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
