package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.ui.components.BackupKeyDialog
import com.jeffers.notimindlite.ui.components.RestoreBackupDialog
import com.jeffers.notimindlite.util.DatabaseExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.SecretKey

@Composable
internal fun RestoreCard(
    prefMgr: PreferenceManager,
    listenerGranted: Boolean,
    onPickBackup: () -> Unit,
    onCreateBackup: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = androidx.compose.ui.res
                    .stringResource(id = R.string.settings_section_restore),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restore, contentDescription = "Restore")
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = androidx.compose.ui.res
                            .stringResource(id = R.string.settings_restore_on_boot_title),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = androidx.compose.ui.res
                            .stringResource(id = R.string.settings_restore_on_boot_desc),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefMgr.isRestoreOnBootEnabled() && listenerGranted,
                    enabled = listenerGranted && !BuildConfig.DEBUG,
                    onCheckedChange = prefMgr::setRestoreOnBootEnabled
                )
            }
            OutlinedButton(
                onClick = onPickBackup,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Restore, contentDescription = "Restore")
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_restore_manual))
            }
            OutlinedButton(
                onClick = onCreateBackup,
                enabled = !BuildConfig.DEBUG,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = "Encrypted backup")
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_dialog_create))
            }
        }
    }
}

data class RestoreDialogConfig(
    val context: Context,
    val scope: CoroutineScope,
    val selectedBackupUri: Uri?,
    val showRestoreDialog: Boolean,
    val restoreSuccessMsg: String,
    val restoreFailureMsg: String,
    val backupKeyBase64: String,
    val pendingBackupKey: SecretKey?,
    val showBackupKeyDialog: Boolean,
    val onRestoreDismiss: () -> Unit,
    val onBackupDismiss: () -> Unit,
    val snackbarHostState: SnackbarHostState,
    val onSyncMessage: (String) -> Unit
)

@Composable
fun RestoreDialogs(config: RestoreDialogConfig) {
    val currentUri = config.selectedBackupUri
    if (config.showRestoreDialog && currentUri != null) {
        val pickedDisplayName = currentUri.lastPathSegment
            ?: androidx.compose.ui.res
                .stringResource(id = R.string.settings_restore_picked_default_name)
        RestoreBackupDialog(
            fileName = pickedDisplayName,
            onDismiss = config.onRestoreDismiss,
            onConfirm = { passphrase ->
                config.onRestoreDismiss()
                config.scope.launch {
                    val result = runRestore(config.context, currentUri, passphrase)
                    val message = if (result.isSuccess) config.restoreSuccessMsg else config.restoreFailureMsg
                    config.snackbarHostState.showSnackbar(message)
                }
            }
        )
    }
    if (config.showBackupKeyDialog && config.pendingBackupKey != null) {
        BackupKeyDialog(
            keyBase64 = config.backupKeyBase64,
            onDismiss = config.onBackupDismiss,
            onConfirm = { passphrase ->
                config.onBackupDismiss()
                config.scope.launch {
                    val result = DatabaseExporter.performEncryptedBackup(
                        config.context,
                        config.pendingBackupKey,
                        passphrase
                    )
                    val error = result.exceptionOrNull()
                    config.onSyncMessage(
                        if (result.isSuccess) {
                            config.context.getString(R.string.backup_created_successfully)
                        } else if (error?.message == "Active internet connection is required to create a backup") {
                            config.context.getString(R.string.backup_network_required)
                        } else {
                            "${config.context.getString(R.string.backup_failed)}: ${error?.localizedMessage}"
                        }
                    )
                }
            }
        )
    }
}

suspend fun runRestore(
    context: Context,
    uri: Uri,
    passphrase: CharArray?
): Result<Unit> = withContext(Dispatchers.IO) {
    val cacheFile = File(context.cacheDir, "restore_input_${System.currentTimeMillis()}.enc")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        } ?: return@withContext Result.failure(IllegalStateException("Could not open backup URI"))
        val key = com.jeffers.notimindlite.domain.backup
            .generateBackupKey(context)
        DatabaseExporter.performRestore(context, cacheFile, key, passphrase)
    } finally {
        passphrase?.fill('\u0000')
        runCatching { cacheFile.delete() }
    }
}
