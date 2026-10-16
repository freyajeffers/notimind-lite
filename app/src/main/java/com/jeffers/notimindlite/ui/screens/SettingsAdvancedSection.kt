package com.jeffers.notimindlite.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R

@Composable
private fun advancedSwitchRow(
    titleRes: Int,
    summaryRes: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summaryRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun advancedNumberRow(
    titleRes: Int,
    summaryRes: Int,
    value: Int,
    width: androidx.compose.ui.unit.Dp,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(summaryRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { it.toIntOrNull()?.let(onValueChange) },
            modifier = Modifier.width(width),
            singleLine = true
        )
    }
}

private data class MigrationDialogConfig(
    val visible: Boolean,
    val context: Context,
    val scope: CoroutineScope,
    val databaseName: String,
    val failureMessage: String,
    val onDismiss: () -> Unit,
    val onMessage: (String?) -> Unit,
    val onMigrationCompleted: () -> Unit
)

@Composable
fun settingsAdvancedSection(
    preferencesRepository: PreferencesRepository,
    onMigrationCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val encryptionEnabled = preferencesRepository.securityManager.dbEncrypted.collectAsState(initial = false).value &&
        preferencesRepository.securityManager.dbEncryptionMode.collectAsState(initial = DbEncryptionMode.NONE).value !=
            DbEncryptionMode.NONE
    val profileId = preferencesRepository.profileManager.activeProfileId.collectAsState().value
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

            advancedSwitchRow(
                R.string.pref_enable_telemetry_title,
                R.string.pref_enable_telemetry_summary,
                enableTelemetry,
                !BuildConfig.DEBUG,
                preferencesRepository::setEnableTelemetry
            )
            advancedSwitchRow(
                R.string.pref_low_memory_mode_title,
                R.string.pref_low_memory_mode_summary,
                lowMemoryMode,
                onCheckedChange = preferencesRepository::setLowMemoryMode
            )
            advancedNumberRow(
                R.string.pref_max_db_mb_title,
                R.string.pref_max_db_mb_summary,
                maxDbMb,
                120.dp,
                preferencesRepository::setMaxDbMb
            )

            migrationControls(
                visible = encryptionEnabled && preflight.plaintextExists,
                migrationMessage = migrationMessage,
                onShowMigration = { showMigrationConfirmation = true },
                vectorCacheMax = vectorCacheMax,
                preferencesRepository = preferencesRepository
            )
        }
    }

    migrationConfirmationDialog(MigrationDialogConfig(
        visible = showMigrationConfirmation,
        context = context,
        scope = scope,
        databaseName = databaseName,
        failureMessage = migrationFailureMessage,
        onDismiss = { showMigrationConfirmation = false },
        onMessage = { migrationMessage = it },
        onMigrationCompleted = onMigrationCompleted
    ))
}

@Composable
private fun migrationControls(
    visible: Boolean,
    migrationMessage: String?,
    onShowMigration: () -> Unit,
    vectorCacheMax: Int,
    preferencesRepository: PreferencesRepository
) {
    if (visible) {
        Text(stringResource(R.string.migration_settings_pending), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onShowMigration) {
            Text(stringResource(R.string.migration_settings_button))
        }
        migrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    advancedNumberRow(
        R.string.pref_vector_cache_max_title,
        R.string.pref_vector_cache_max_summary,
        vectorCacheMax,
        120.dp,
        preferencesRepository::setVectorCacheMax
    )
}

@Composable
private fun migrationConfirmationDialog(config: MigrationDialogConfig) {
    if (!config.visible) return
    AlertDialog(
        onDismissRequest = config.onDismiss,
        title = { Text(stringResource(R.string.migration_settings_confirm_title)) },
        text = { Text(stringResource(R.string.migration_settings_confirm_description)) },
        confirmButton = {
            Button(onClick = {
                config.onDismiss()
                config.scope.launch {
                    val message = withContext(Dispatchers.IO) {
                        EncryptionMigrationConsent.set(config.context, EncryptionMigrationConsent.Decision.ACCEPTED)
                        AppDatabase.resetInstance()
                        val state = MigrationRunner(config.context).migrateLegacyDatabase(config.databaseName)
                        if (state == MigrationState.COMPLETE) null else config.failureMessage
                    }
                    config.onMessage(message)
                    if (message == null) config.onMigrationCompleted()
                }
            }) { Text(stringResource(R.string.migration_settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = config.onDismiss) {
                Text(stringResource(R.string.migration_settings_cancel))
            }
        }
    )
}
