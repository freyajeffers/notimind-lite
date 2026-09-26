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
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.util.PreferencesBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsPreferencesBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val temp = java.io.File(context.cacheDir, "preferences-export.json")
                    PreferencesBackup.exportPreferences(context, temp.absolutePath)
                    context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                        ?: error("Unable to open destination")
                    temp.delete()
                }
                context.getString(R.string.pref_backup_exported)
            }.getOrElse { context.getString(R.string.pref_backup_failed, it.message ?: "unknown error") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val temp = java.io.File(context.cacheDir, "preferences-import.json")
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        ?: error("Unable to open source")
                    PreferencesBackup.importPreferences(context, temp.absolutePath)
                    temp.delete()
                }
                context.getString(R.string.pref_backup_imported)
            }.getOrElse { context.getString(R.string.pref_backup_failed, it.message ?: "unknown error") }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pref_backup_settings_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pref_backup_settings_summary), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("notimind-preferences.json") }) { Text(stringResource(R.string.pref_backup_export)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) { Text(stringResource(R.string.pref_backup_import)) }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
