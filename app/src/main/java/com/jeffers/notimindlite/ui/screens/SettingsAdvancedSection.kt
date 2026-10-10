package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.DbEncryptionMode
import com.jeffers.notimindlite.migration.EncryptionMigrationConsent
import com.jeffers.notimindlite.migration.MigrationRunner
import com.jeffers.notimindlite.migration.MigrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R

@Suppress("LongMethod", "FunctionNaming")
@Composable
fun SettingsAdvancedSection(
    preferencesRepository: PreferencesRepository,
    onMigrationCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val encryptionEnabled = preferencesRepository.dbEncrypted.collectAsState(initial = false).value &&
        preferencesRepository.dbEncryptionMode.collectAsState(initial = DbEncryptionMode.NONE).value !=
            DbEncryptionMode.NONE
    val profileId = preferencesRepository.activeProfileId.collectAsState().value
    val databaseName = if (profileId == PreferencesRepository.DEFAULT_PROFILE_ID) {
        AppDatabase.CE_DATABASE_NAME
    } else {
        "${AppDatabase.CE_DATABASE_NAME}_$profileId"
    }
    val preflight = remember(databaseName) { MigrationRunner(context).preflight(databaseName) }
    var showMigrationConfirmation by remember { mutableStateOf(false) }
    var migrationMessage by remember { mutableStateOf<String?>(null) }
    val migrationFailureMessage = stringResource(R.string.migration_settings_failure)
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
                Switch(checked = enableTelemetry, onCheckedChange = preferencesRepository::setEnableTelemetry, enabled = !BuildConfig.DEBUG)
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

            if (encryptionEnabled && preflight.plaintextExists) {
                Text(stringResource(R.string.migration_settings_pending), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { showMigrationConfirmation = true }) {
                    Text(stringResource(R.string.migration_settings_button))
                }
                migrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

    if (showMigrationConfirmation) {
        AlertDialog(
            onDismissRequest = { showMigrationConfirmation = false },
            title = { Text(stringResource(R.string.migration_settings_confirm_title)) },
            text = { Text(stringResource(R.string.migration_settings_confirm_description)) },
            confirmButton = {
                Button(onClick = {
                    showMigrationConfirmation = false
                    scope.launch {
                        migrationMessage = withContext(Dispatchers.IO) {
                            EncryptionMigrationConsent.set(context, EncryptionMigrationConsent.Decision.ACCEPTED)
                            AppDatabase.resetInstance()
                            val state = MigrationRunner(context).migrateLegacyDatabase(databaseName)
                            if (state == MigrationState.COMPLETE) {
                                null
                            } else {
                                migrationFailureMessage
                            }
                        }
                        if (migrationMessage == null) onMigrationCompleted()
                    }
                }) { Text(stringResource(R.string.migration_settings_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showMigrationConfirmation = false }) {
                    Text(stringResource(R.string.migration_settings_cancel))
                }
            }
        )
    }
}
