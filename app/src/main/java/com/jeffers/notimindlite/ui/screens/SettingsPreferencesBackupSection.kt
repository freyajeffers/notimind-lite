package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.PreferencesBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun SettingsPreferencesBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    // Pre-fetch strings to avoid @Composable calls inside coroutines and satisfy Lint
    val strExported = stringResource(R.string.pref_backup_exported)
    val strImported = stringResource(R.string.pref_backup_imported)
    val strFailed = stringResource(R.string.pref_backup_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { exportPreferencesToUri(context, uri) }
                message = if (result.isSuccess) {
                    strExported
                } else {
                    backupFailureMessage(strFailed, result)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { importPreferencesFromUri(context, uri) }
                message = if (result.isSuccess) {
                    strImported
                } else {
                    backupFailureMessage(strFailed, result)
                }
            }
        }
    }

    val preferences = remember { PreferencesRepository(context) }
    val exportFormats by preferences.exportManager.formats.collectAsState()
    val exportAnonymize by preferences.exportManager.anonymize.collectAsState()

    PreferencesBackupCard(
        PreferencesBackupCardConfig(
            exportFormats = exportFormats,
            exportAnonymize = exportAnonymize,
            onFormatsChange = preferences.exportManager::setFormats,
            onAnonymizeChange = preferences.exportManager::setAnonymize,
            onExport = { exportLauncher.launch("notimind-preferences.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
            message = message,
            enabled = !BuildConfig.DEBUG
        )
    )
}

private fun backupFailureMessage(template: String, result: Result<*>): String =
    String.format(Locale.getDefault(), template, result.exceptionOrNull()?.message ?: "unknown error")

private suspend fun exportPreferencesToUri(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val temp = java.io.File(context.cacheDir, "preferences-export.json")
        PreferencesBackup.exportPreferences(context, temp.absolutePath)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            temp.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open destination")
        temp.delete()
    }
}

private suspend fun importPreferencesFromUri(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val temp = java.io.File(context.cacheDir, "preferences-import.json")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open source")
        PreferencesBackup.importPreferences(context, temp.absolutePath)
        temp.delete()
    }
}
