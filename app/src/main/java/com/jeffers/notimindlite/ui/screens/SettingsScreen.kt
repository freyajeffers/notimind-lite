package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.auth.UserSession
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.jeffers.notimindlite.data.sync.SyncWorker
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.ui.components.RestoreBackupDialog
import com.jeffers.notimindlite.ui.components.BackupKeyDialog
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.domain.backup.generateBackupKey
import com.jeffers.notimindlite.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.SecretKey

@Composable
@Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MaxLineLength",
    "FunctionNaming"
) // SettingsScreen composes the five sections (Account, Sync, Privacy, Listener, Restore)
   // and the version footer in one screen Composable; splitting would fragment
   // PreferenceManager + auth state lifetimes. Composable PascalCase is required by
   // the Compose API and detekt's FunctionNaming rule does not exempt it.
fun SettingsScreen(
    authManager: AuthManager,
    db: AppDatabase,
    preferencesRepository: com.jeffers.notimindlite.data.local.PreferencesRepository,
    webClientId: String = ""
) {
    val session by authManager.session.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    // H1: file picker + restore dialog plumbing. The picker runs on the UI thread but
    // copies the URI to a local cache File on Dispatchers.IO before invoking performRestore.
    // Snackbar feedback surfaces success/failure without leaving the Settings screen.
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBackupKeyDialog by remember { mutableStateOf(false) }
    var backupKeyBase64 by remember { mutableStateOf("") }
    var pendingBackupKey by remember { mutableStateOf<SecretKey?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val restoreSuccessMsg = stringResource(id = R.string.settings_restore_success)
    val restoreFailureMsg = stringResource(id = R.string.settings_restore_failure)

    val pickBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedBackupUri = uri
        showRestoreDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(0.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (session.isAuthenticated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.settings_profile_icon_cd))
                        Column {
                            Text(
                                text = session.displayName ?: stringResource(id = R.string.settings_default_display_name),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = session.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            authManager.signOut()
                            SyncWorker.cancelPeriodicSync(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.settings_sign_out_cd))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_sign_out))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_sign_in_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                authManager.signInWithGoogle(webClientId)
                                SyncWorker.schedulePeriodicSync(context)
                            }
                        },
                        enabled = !session.isAuthenticating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = stringResource(R.string.settings_sign_in_cd))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (session.isAuthenticating) R.string.settings_signing_in else R.string.settings_sign_in))
                    }

                    session.error?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Notification Access card moved to top for discoverability
        val prefMgrTop = remember { PreferenceManager(context) }
        val lifecycleOwnerTop = LocalLifecycleOwner.current
        var listenerGrantedTop by remember { mutableStateOf(checkNotificationPermission(context)) }
        DisposableEffect(lifecycleOwnerTop) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    listenerGrantedTop = checkNotificationPermission(context)
                }
            }
            lifecycleOwnerTop.lifecycle.addObserver(observer)
            onDispose { lifecycleOwnerTop.lifecycle.removeObserver(observer) }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (listenerGrantedTop)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.settings_section_listener), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = if (listenerGrantedTop) Icons.Default.Notifications else Icons.Default.NotificationsOff, contentDescription = if (listenerGrantedTop) "Notification access enabled" else "Notification access disabled", tint = if (listenerGrantedTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(text = stringResource(id = if (listenerGrantedTop) R.string.settings_listener_granted_desc else R.string.settings_listener_missing_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                Button(onClick = { val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }, colors = ButtonDefaults.buttonColors(containerColor = if (listenerGrantedTop) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.error, contentColor = if (listenerGrantedTop) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onError)) {
                    Text(stringResource(id = if (listenerGrantedTop) R.string.settings_listener_open else R.string.settings_listener_grant))
                }
            }
        }

        // Boot & Restore card placed near top for quick opt-in/out.
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.settings_section_restore), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.settings_restore_icon_cd))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.settings_restore_on_boot_title), style = MaterialTheme.typography.bodyLarge)
                        Text(text = stringResource(id = R.string.settings_restore_on_boot_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefMgrTop.isRestoreOnBootEnabled() && listenerGrantedTop, enabled = listenerGrantedTop && !BuildConfig.DEBUG, onCheckedChange = { prefMgrTop.setRestoreOnBootEnabled(it) })
                }
                OutlinedButton(onClick = { pickBackupLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.settings_restore_icon_cd))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_restore_manual))
                }
            }
        }

        // Continue with other sections
        SettingsCaptureSection(preferencesRepository = preferencesRepository)
        ProfileManagerSection(repository = preferencesRepository)
        SettingsPreferencesSection(preferencesRepository = preferencesRepository)
        SettingsSyncSection(preferencesRepository = preferencesRepository)
        SettingsPrivacySection(preferencesRepository = preferencesRepository)
        SettingsAdvancedSection(preferencesRepository = preferencesRepository)
        SettingsPreferencesBackupSection()

        if (session.isAuthenticated) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_sync),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(R.string.settings_sync_idle),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = {
                            session.uid?.let { uid ->
                                scope.launch {
                                    isSyncing = true
                                    val repo = FirestoreSyncRepository(db, profileId = preferencesRepository.activeProfileId.value)

                                    val secretKey: SecretKey = generateBackupKey(context)

                                    val res = repo.sync(uid, secretKey)
                                    isSyncing = false
                                    syncMessage = if (res.isSuccess) {
                                        context.getString(R.string.settings_sync_success, res.getOrDefault(0))
                                    } else {
                                        context.getString(R.string.settings_sync_failure, res.exceptionOrNull()?.localizedMessage.orEmpty())
                                    }
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = stringResource(R.string.settings_cloud_sync_cd))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (isSyncing) R.string.settings_sync_now_loading else R.string.settings_sync_now_idle))
                    }

                    OutlinedButton(
                        onClick = {
                            if (!NetworkUtils.isInternetAvailable(context)) {
                                syncMessage = context.getString(R.string.backup_network_required)
                            } else {
                                val secretKey = generateBackupKey(context)
                                pendingBackupKey = secretKey
                                backupKeyBase64 = com.jeffers.notimindlite.data.local.BackupKeyCodec.encode(secretKey)
                                showBackupKeyDialog = true
                            }
                        },
                        enabled = !isSyncing && !BuildConfig.DEBUG
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.settings_restore_icon_cd))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_create_backup))
                    }

                    syncMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_section_privacy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_strict_privacy_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_strict_privacy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = remember { PreferenceManager(context).isStrictPrivacyEnabled() },
                        onCheckedChange = { enabled ->
                            PreferenceManager(context).setStrictPrivacyEnabled(enabled)
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_pii_redaction_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_pii_redaction_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    var piiRedactionChecked by remember { mutableStateOf(PreferenceManager(context).isPiiRedactionEnabled()) }
                    Switch(
                        checked = piiRedactionChecked,
                        enabled = !BuildConfig.DEBUG,
                        onCheckedChange = { enabled ->
                            piiRedactionChecked = enabled
                            PreferenceManager(context).setPiiRedactionEnabled(enabled)
                        }
                    )
                }
            }
        }


        // F-N usability [2026-09-06]: version footer — gives users a build identifier
        // they can quote in support tickets. Looked up via PackageManager at composable
        // scope (not inside try/catch around a composable) so the build info is cached
        // for recomposition.
        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            }.getOrDefault("?")
        }
        val versionCode = remember {
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (BuildConfig.SUPPORTS_LONG_VERSION_CODE) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode
                }
            }.getOrDefault(0)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.settings_version_footer, versionName, versionCode),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )

        // H1: render the restore dialog (if a backup file has been picked). The dialog reads
        // the passphrase from the user; the snackbar host inside the Column surfaces the
        // outcome of performRestore() without leaving the Settings screen.
        val currentUri = selectedBackupUri
        if (showRestoreDialog && currentUri != null) {
            val pickedDisplayName = currentUri.lastPathSegment
                ?: stringResource(id = R.string.settings_restore_picked_default_name)
            RestoreBackupDialog(
                fileName = pickedDisplayName,
                onDismiss = { showRestoreDialog = false },
                onConfirm = { passphrase ->
                    showRestoreDialog = false
                    val uriToRestore = currentUri
                    scope.launch {
                        val result = runRestore(
                            context = context,
                            uri = uriToRestore,
                            passphrase = passphrase,
                        )
                        val msg = if (result.isSuccess) restoreSuccessMsg else restoreFailureMsg
                        snackbarHostState.showSnackbar(msg)
                    }
                },
            )
        }

        if (showBackupKeyDialog && pendingBackupKey != null) {
            BackupKeyDialog(
                keyBase64 = backupKeyBase64,
                onDismiss = {
                    showBackupKeyDialog = false
                    pendingBackupKey = null
                },
                onConfirm = { passphrase ->
                    showBackupKeyDialog = false
                    val secretKey = pendingBackupKey
                    pendingBackupKey = null
                    if (secretKey != null) {
                        scope.launch {
                            val result = DatabaseExporter.performEncryptedBackup(context, secretKey, passphrase)
                            syncMessage = if (result.isSuccess) {
                                "Backup created successfully"
                            } else {
                                "Backup failed: ${result.exceptionOrNull()?.localizedMessage}"
                            }
                        }
                    }
                }
            )
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

// H1 helper: copy the user-picked URI into the app cache and invoke performRestore.
// Runs on Dispatchers.IO; returns Result<Unit> so the UI can branch on success/failure.
// We use the device's hardware-bound key (generateBackupKey(context)) for same-device restore;
// the passphrase supplied by the user is forwarded for cross-device/post-uninstall unwrap.
private suspend fun runRestore(
    context: Context,
    uri: Uri,
    passphrase: CharArray?,
): Result<Unit> = withContext(Dispatchers.IO) {
    val cacheFile = File(context.cacheDir, "restore_input_${System.currentTimeMillis()}.enc")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        } ?: return@withContext Result.failure(IllegalStateException("Could not open backup URI"))
        val key = generateBackupKey(context)
        DatabaseExporter.performRestore(context, cacheFile, key, passphrase)
    } finally {
        passphrase?.fill('\u0000')
        // Best-effort cache cleanup; ignore failures to keep restore flow simple.
        runCatching { cacheFile.delete() }
    }
}
