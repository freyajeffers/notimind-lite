package com.jeffers.notimindlite.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.ExportRangePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val FORMAT_JSON = "json"
private const val MIME_OCTET_STREAM = "application/octet-stream"
private const val FORMAT_CSV = "csv"
private const val FORMATS_SEPARATOR = ','

/**
 * Notification export surface: time window, app filter, live count preview and an
 * explicit confirmation step before the share sheet opens.
 */
@Composable
fun SettingsExportSection(
    preferencesRepository: PreferencesRepository,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val formats by preferencesRepository.exportManager.formats.collectAsState()
    val requirePassphrase by preferencesRepository.securityManager.requirePassphrase.collectAsState()
    val requireDeviceConfirmation by
        preferencesRepository.securityManager.exportRequiresBiometric.collectAsState()
    val encryptedExports by preferencesRepository.securityManager.encryptedExports.collectAsState()

    val allowedFormats = remember(formats) { parseSupportedFormats(formats) }
    var format by remember { mutableStateOf(FORMAT_JSON) }
    LaunchedEffect(allowedFormats) {
        if (format !in allowedFormats) {
            format = allowedFormats.firstOrNull() ?: FORMAT_JSON
        }
    }

    val notificationState = rememberExportableNotifications()
    val notifications = notificationState.items
    val loaded = notificationState.loaded

    val packages = remember(notifications) { distinctPackages(notifications) }
    var presetName by rememberSaveable { mutableStateOf(ExportRangePreset.ALL_TIME.name) }
    val preset = ExportRangePreset.valueOf(presetName)
    var selectedPackages by remember(packages) { mutableStateOf(packages.map { it.packageName }.toSet()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var passphrase by remember { mutableStateOf("") }

    val selection = remember(preset, selectedPackages) {
        preset.toSelection(System.currentTimeMillis()).copy(packageNames = selectedPackages)
    }
    val selected = remember(notifications, selection) {
        DatabaseExporter.selectForExport(notifications, selection)
    }

    val delivery = rememberExportDelivery(
        ExportDeliveryConfig(
            notifications = notifications,
            preset = preset,
            selectedPackages = selectedPackages,
            format = format,
            passphrase = passphrase,
            requireDeviceConfirmation = requireDeviceConfirmation,
            snackbarHostState = snackbarHostState,
            onExportFinished = {
                pendingExport = null
                passphrase = ""
            },
        )
    )

    ExportCard(
        ExportCardConfig(
            preset = preset,
            onPresetChange = { presetName = it.name },
            packages = packages,
            selectedPackages = selectedPackages,
            onChooseApps = { showAppPicker = true },
            allowedFormats = allowedFormats,
            format = format,
            onFormatChange = { format = it },
            selectedCount = selected.size,
            totalCount = notifications.size,
            canExport = loaded &&
                selected.isNotEmpty() &&
                format in allowedFormats &&
                (!requirePassphrase || passphrase.isNotEmpty()),
            loaded = loaded,
            onExportClick = {
                pendingExport = PendingExport(
                    count = selected.size,
                    preset = preset,
                    appCount = selectedPackages.size,
                    format = format,
                )
            },
        )
    )

    if (showAppPicker) {
        AppPickerDialog(
            packages = packages,
            selectedPackages = selectedPackages,
            onSelectionChange = { selectedPackages = it },
            onDismiss = { showAppPicker = false },
        )
    }
    pendingExport?.let { pending ->
        ExportConfirmDialog(
            ExportConfirmConfig(
                selectedCount = pending.count,
                preset = pending.preset,
                selectedAppCount = pending.appCount,
                format = pending.format,
                encryptedExports = encryptedExports,
                requirePassphrase = requirePassphrase,
                passphrase = passphrase,
                onPassphraseChange = { passphrase = it },
                onShare = delivery.onShare,
                onSave = delivery.onSave,
                onDismiss = { pendingExport = null },
            )
        )
    }
}

private data class ExportAppOption(
    val packageName: String,
    val appName: String,
)

private data class ExportCardConfig(
    val preset: ExportRangePreset,
    val onPresetChange: (ExportRangePreset) -> Unit,
    val packages: List<ExportAppOption>,
    val selectedPackages: Set<String>,
    val onChooseApps: () -> Unit,
    val allowedFormats: List<String>,
    val format: String,
    val onFormatChange: (String) -> Unit,
    val selectedCount: Int,
    val totalCount: Int,
    val canExport: Boolean,
    val loaded: Boolean,
    val onExportClick: () -> Unit,
)

private data class ExportConfirmConfig(
    val selectedCount: Int,
    val preset: ExportRangePreset,
    val selectedAppCount: Int,
    val format: String,
    val encryptedExports: Boolean,
    val requirePassphrase: Boolean,
    val passphrase: String,
    val onPassphraseChange: (String) -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

private data class DeviceConfirmationGate(
    val required: Boolean,
    val onExport: (Boolean) -> Unit,
    val onCancelled: () -> Unit,
    val onBlocked: () -> Unit,
)

private data class ExportDeliveryConfig(
    val notifications: List<NotificationEntity>,
    val preset: ExportRangePreset,
    val selectedPackages: Set<String>,
    val format: String,
    val passphrase: String,
    val requireDeviceConfirmation: Boolean,
    val snackbarHostState: SnackbarHostState,
    val onExportFinished: () -> Unit,
)

private data class ExportDelivery(
    val onShare: () -> Unit,
    val onSave: () -> Unit,
)

/**
 * Builds the export once, then delivers it to either the system share sheet or a
 * user-chosen document location, behind the device-credential gate when required.
 *
 * Creating the file first means Share and Save produce byte-identical output and the
 * expensive encryption runs once per request.
 */
@Composable
private fun rememberExportDelivery(config: ExportDeliveryConfig): ExportDelivery {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val successMessage = stringResource(R.string.export_success)
    val failedMessage = stringResource(R.string.export_failed)
    val cancelledMessage = stringResource(R.string.export_cancelled)
    val noCredentialMessage = stringResource(R.string.export_no_credential)
    val savedMessage = stringResource(R.string.export_saved)
    val saveFailedMessage = stringResource(R.string.export_save_failed)

    var saveRequested by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_OCTET_STREAM)
    ) { uri ->
        val file = pendingSaveFile
        pendingSaveFile = null
        if (uri == null || file == null) {
            scope.launch { config.snackbarHostState.showSnackbar(cancelledMessage) }
        } else {
            val saved = DatabaseExporter.saveExportTo(context, file, uri)
            if (saved) {
                // The user now owns a copy; drop the transient cache artifact.
                file.delete()
            }
            scope.launch {
                config.snackbarHostState.showSnackbar(if (saved) savedMessage else saveFailedMessage)
            }
        }
    }

    val performExport: (Boolean) -> Unit = { deviceConfirmed ->
        val liveSelection = config.preset
            .toSelection(System.currentTimeMillis())
            .copy(packageNames = config.selectedPackages)
        val file = DatabaseExporter.createExport(
            context = context,
            notifications = config.notifications,
            isJson = config.format == FORMAT_JSON,
            passphrase = config.passphrase.takeIf { it.isNotEmpty() }?.toCharArray(),
            biometricAuthenticated = deviceConfirmed,
            selection = liveSelection,
        )
        config.onExportFinished()
        when {
            file == null -> {
                scope.launch { config.snackbarHostState.showSnackbar(failedMessage) }
            }

            saveRequested -> {
                pendingSaveFile = file
                saveLauncher.launch(file.name)
            }

            else -> {
                val shared = DatabaseExporter.shareExport(context, file, config.format == FORMAT_JSON)
                scope.launch {
                    config.snackbarHostState.showSnackbar(if (shared) successMessage else failedMessage)
                }
            }
        }
    }

    val gate = rememberDeviceConfirmedExport(
        DeviceConfirmationGate(
            required = config.requireDeviceConfirmation,
            onExport = performExport,
            onCancelled = {
                config.onExportFinished()
                scope.launch { config.snackbarHostState.showSnackbar(cancelledMessage) }
            },
            onBlocked = {
                config.onExportFinished()
                scope.launch { config.snackbarHostState.showSnackbar(noCredentialMessage) }
            },
        )
    )

    return ExportDelivery(
        onShare = {
            saveRequested = false
            gate()
        },
        onSave = {
            saveRequested = true
            gate()
        },
    )
}

/**
 * Snapshot of what the user saw when they tapped Export. The confirmation
 * dialog renders from this, so it can never disagree with the live preview.
 */
private data class PendingExport(
    val count: Int,
    val preset: ExportRangePreset,
    val appCount: Int,
    val format: String,
)

/**
 * Wraps export behind device-credential confirmation when the security
 * preference is enabled, and returns the trigger for the confirm dialog.
 */
@Composable
private fun rememberDeviceConfirmedExport(gate: DeviceConfirmationGate): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            gate.onExport(true)
        } else {
            gate.onCancelled()
        }
    }
    return {
        if (!gate.required) {
            gate.onExport(false)
        } else {
            val intent = deviceConfirmationIntent(context)
            if (intent == null) gate.onBlocked() else launcher.launch(intent)
        }
    }
}

@Composable
private fun ExportCard(config: ExportCardConfig) {
    val preset = config.preset
    val onPresetChange = config.onPresetChange
    val allowedFormats = config.allowedFormats
    val onFormatChange = config.onFormatChange
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.export_section_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RangeChips(preset = preset, onPresetChange = onPresetChange)
            AppFilterRow(config)
            FormatRow(allowedFormats = allowedFormats, format = config.format, onFormatChange = onFormatChange)
            SelectionSummary(config)
            Button(
                onClick = config.onExportClick,
                enabled = config.canExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_action))
            }
            if (config.loaded && config.selectedCount == 0) {
                Text(
                    text = stringResource(R.string.export_no_selection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RangeChips(preset: ExportRangePreset, onPresetChange: (ExportRangePreset) -> Unit) {
    Text(
        text = stringResource(R.string.export_range_label),
        style = MaterialTheme.typography.bodyMedium
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = preset == ExportRangePreset.ALL_TIME,
            onClick = { onPresetChange(ExportRangePreset.ALL_TIME) },
            label = { Text(stringResource(R.string.export_range_all_time)) }
        )
        FilterChip(
            selected = preset == ExportRangePreset.LAST_24_HOURS,
            onClick = { onPresetChange(ExportRangePreset.LAST_24_HOURS) },
            label = { Text(stringResource(R.string.export_range_24h)) }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = preset == ExportRangePreset.LAST_7_DAYS,
            onClick = { onPresetChange(ExportRangePreset.LAST_7_DAYS) },
            label = { Text(stringResource(R.string.export_range_7d)) }
        )
        FilterChip(
            selected = preset == ExportRangePreset.LAST_30_DAYS,
            onClick = { onPresetChange(ExportRangePreset.LAST_30_DAYS) },
            label = { Text(stringResource(R.string.export_range_30d)) }
        )
    }
}

@Composable
private fun AppFilterRow(config: ExportCardConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = appFilterLabel(config),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = config.onChooseApps, enabled = config.packages.isNotEmpty()) {
                Text(stringResource(R.string.export_choose_apps))
            }
        }
        // A disabled control with no explanation is indistinguishable from a broken one.
        if (config.loaded && config.packages.isEmpty()) {
            Text(
                text = stringResource(R.string.export_no_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun appFilterLabel(config: ExportCardConfig): String {
    val total = config.packages.size
    val selected = config.selectedPackages.size
    return if (total == 0) {
        stringResource(R.string.export_no_apps_label)
    } else if (selected == total) {
        stringResource(R.string.export_apps_all, total)
    } else {
        stringResource(R.string.export_apps_selected, selected, total)
    }
}

@Composable
private fun FormatRow(allowedFormats: List<String>, format: String, onFormatChange: (String) -> Unit) {
    Text(
        text = stringResource(R.string.export_format_label),
        style = MaterialTheme.typography.bodyMedium
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (FORMAT_JSON in allowedFormats) {
            RadioButton(selected = format == FORMAT_JSON, onClick = { onFormatChange(FORMAT_JSON) })
            Text("JSON", style = MaterialTheme.typography.bodyMedium)
        }
        if (FORMAT_CSV in allowedFormats) {
            RadioButton(selected = format == FORMAT_CSV, onClick = { onFormatChange(FORMAT_CSV) })
            Text("CSV", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SelectionSummary(config: ExportCardConfig) {
    Text(
        text = if (config.loaded) {
            stringResource(R.string.export_selection_count, config.selectedCount, config.totalCount)
        } else {
            stringResource(R.string.export_loading)
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun AppPickerDialog(
    packages: List<ExportAppOption>,
    selectedPackages: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val allPackages = remember(packages) { packages.map { it.packageName }.toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_app_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSelectionChange(allPackages) }) {
                        Text(stringResource(R.string.export_app_picker_all))
                    }
                    TextButton(onClick = { onSelectionChange(emptySet()) }) {
                        Text(stringResource(R.string.export_app_picker_none))
                    }
                }
                packages.forEach { option ->
                    AppPickerRow(
                        option = option,
                        checked = option.packageName in selectedPackages,
                        onToggle = {
                            val next = if (option.packageName in selectedPackages) {
                                selectedPackages - option.packageName
                            } else {
                                selectedPackages + option.packageName
                            }
                            onSelectionChange(next)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_app_picker_done)) }
        }
    )
}

@Composable
private fun AppPickerRow(option: ExportAppOption, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = option.appName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportConfirmDialog(config: ExportConfirmConfig) {
    val rangeLabel = presetLabel(config.preset)
    AlertDialog(
        onDismissRequest = config.onDismiss,
        title = { Text(stringResource(R.string.export_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(
                        R.string.export_confirm_body,
                        config.selectedCount,
                        rangeLabel,
                        config.selectedAppCount.toString(),
                        config.format.uppercase()
                    )
                )
                Text(
                    text = stringResource(R.string.export_confirm_privacy),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        if (config.encryptedExports) {
                            R.string.export_confirm_encrypted
                        } else {
                            R.string.export_confirm_plaintext
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (config.requirePassphrase) {
                    Text(
                        text = stringResource(R.string.export_passphrase_required),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = config.passphrase,
                        onValueChange = config.onPassphraseChange,
                        label = { Text(stringResource(R.string.export_passphrase_label)) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val canDeliver = config.selectedCount > 0 &&
                    (!config.requirePassphrase || config.passphrase.isNotEmpty())
                TextButton(onClick = config.onSave, enabled = canDeliver) {
                    Text(stringResource(R.string.export_save_action))
                }
                TextButton(onClick = config.onShare, enabled = canDeliver) {
                    Text(stringResource(R.string.export_share_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = config.onDismiss) { Text(stringResource(R.string.export_cancel)) }
        }
    )
}

@Composable
private fun presetLabel(preset: ExportRangePreset): String = when (preset) {
    ExportRangePreset.ALL_TIME -> stringResource(R.string.export_range_all_time)
    ExportRangePreset.LAST_24_HOURS -> stringResource(R.string.export_range_24h)
    ExportRangePreset.LAST_7_DAYS -> stringResource(R.string.export_range_7d)
    ExportRangePreset.LAST_30_DAYS -> stringResource(R.string.export_range_30d)
}

private fun parseSupportedFormats(raw: String): List<String> = raw
    .split(FORMATS_SEPARATOR)
    .map { it.trim().lowercase() }
    .filter { it == FORMAT_JSON || it == FORMAT_CSV }
    .distinct()

private suspend fun loadExportableNotifications(context: Context): List<NotificationEntity> =
    withContext(Dispatchers.IO) {
        runCatching {
            AppDatabase.getDatabase(context).notificationDao().getAllNotificationsList()
        }.getOrDefault(emptyList())
    }

private data class LoadedNotifications(
    val items: List<NotificationEntity>,
    val loaded: Boolean,
)

@Composable
private fun rememberExportableNotifications(): LoadedNotifications {
    var state by remember { mutableStateOf(LoadedNotifications(emptyList(), false)) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        state = LoadedNotifications(loadExportableNotifications(context), true)
    }
    return state
}

private fun distinctPackages(notifications: List<NotificationEntity>): List<ExportAppOption> =
    notifications
        .map { ExportAppOption(it.packageName, it.appName) }
        .distinctBy { it.packageName }
        .sortedBy { it.appName.lowercase() }

@Suppress("DEPRECATION")
private fun deviceConfirmationIntent(context: Context): Intent? {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
    if (!keyguard.isDeviceSecure) return null
    return keyguard.createConfirmDeviceCredentialIntent(
        context.getString(R.string.export_confirm_title),
        context.getString(R.string.export_confirm_privacy)
    )
}
