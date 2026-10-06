package com.jeffers.notimindlite.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.util.PreferencesBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsPreferencesBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
<<<<<<< Updated upstream
    val exportedMessage = stringResource(R.string.pref_backup_exported)
    val importedMessage = stringResource(R.string.pref_backup_imported)
    val failedMessageFormat = stringResource(R.string.pref_backup_failed)
=======

    // Pre-fetch strings to avoid @Composable calls inside coroutines and satisfy Lint
    val strExported = stringResource(R.string.pref_backup_exported)
    val strImported = stringResource(R.string.pref_backup_imported)
    val strFailed = stringResource(R.string.pref_backup_failed)

>>>>>>> Stashed changes
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val temp = java.io.File(context.cacheDir, "preferences-export.json")
                    PreferencesBackup.exportPreferences(context, temp.absolutePath)
                    context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                        ?: error("Unable to open destination")
                    temp.delete()
                }
<<<<<<< Updated upstream
                exportedMessage
            }.getOrElse {
                String.format(Locale.getDefault(), failedMessageFormat, it.message ?: "unknown error")
=======
                true
            }
            message = if (result.isSuccess) {
                strExported
            } else {
                // For parameterized strings, we still use context.getString since we need to inject the error message
                context.getString(R.string.pref_backup_failed, result.exceptionOrNull()?.message ?: "unknown error")
>>>>>>> Stashed changes
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val temp = java.io.File(context.cacheDir, "preferences-import.json")
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        ?: error("Unable to open source")
                    PreferencesBackup.importPreferences(context, temp.absolutePath)
                    temp.delete()
                }
<<<<<<< Updated upstream
                importedMessage
            }.getOrElse {
                String.format(Locale.getDefault(), failedMessageFormat, it.message ?: "unknown error")
=======
                true
            }
            message = if (result.isSuccess) {
                strImported
            } else {
                context.getString(R.string.pref_backup_failed, result.exceptionOrNull()?.message ?: "unknown error")
>>>>>>> Stashed changes
            }
        }
    }

    val preferences = remember { PreferencesRepository(context) }
    val exportFormats by preferences.exportFormats.collectAsState()
    val exportAnonymize by preferences.exportAnonymize.collectAsState()

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pref_backup_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pref_backup_settings_summary), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = exportFormats,
                onValueChange = preferences::setExportFormats,
                label = { Text(stringResource(R.string.pref_export_formats)) },
                supportingText = { Text(stringResource(R.string.pref_export_formats_summary)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !BuildConfig.DEBUG
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_export_anonymize), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.pref_export_anonymize_summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = exportAnonymize, onCheckedChange = preferences::setExportAnonymize, enabled = !BuildConfig.DEBUG)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("notimind-preferences.json") }, enabled = !BuildConfig.DEBUG) { Text(stringResource(R.string.pref_backup_export)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }, enabled = !BuildConfig.DEBUG) { Text(stringResource(R.string.pref_backup_import)) }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
