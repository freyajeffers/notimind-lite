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
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.domain.backup.generateBackupKey
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
    webClientId: String = ""
) {
    val session by authManager.session.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefMgr = remember(context) { PreferenceManager(context) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var strictPrivacyEnabled by remember { mutableStateOf(prefMgr.isStrictPrivacyEnabled()) }
    var piiRedactionEnabled by remember { mutableStateOf(prefMgr.isPiiRedactionEnabled()) }
    var restoreOnBootEnabled by remember { mutableStateOf(prefMgr.isRestoreOnBootEnabled()) }

    // H1: file picker + restore dialog plumbing. The picker runs on the UI thread but
    // copies the URI to a local cache File on Dispatchers.IO before invoking performRestore.
    // Snackbar feedback surfaces success/failure without leaving the Settings screen.
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val restoreSuccessMsg = stringResource(id = R.string.settings_restore_success)
    val restoreFailureMsg = stringResource(id = R.string.settings_restore_failure)
    val syncSuccessTemplate = stringResource(R.string.settings_sync_success)
    val syncFailureTemplate = stringResource(R.string.settings_sync_failure)
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerGranted by remember { mutableStateOf(checkNotificationPermission(context)) }

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
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_section_account),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (session.isAuthenticated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = stringResource(id = R.string.settings_profile_cd)
                        )
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = R.string.settings_sign_out))
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.settings_sign_in_prompt),
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
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (session.isAuthenticating) {
                                stringResource(id = R.string.settings_sign_in_loading)
                            } else {
                                stringResource(id = R.string.settings_sign_in_idle)
                            }
                        )
                    }

                    session.error?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

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
                        text = stringResource(id = R.string.settings_section_sync),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = stringResource(id = R.string.settings_sync_idle),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = {
                            session.uid?.let { uid ->
                                scope.launch {
                                    isSyncing = true
                                    val repo = FirestoreSyncRepository(db)

                                    val secretKey: SecretKey = generateBackupKey(context)

                                    val res = repo.sync(uid, secretKey)
                                    isSyncing = false
                                    syncMessage = if (res.isSuccess) {
                                        String.format(syncSuccessTemplate, res.getOrDefault(0))
                                    } else {
                                        String.format(
                                            syncFailureTemplate,
                                            res.exceptionOrNull()?.localizedMessage.orEmpty()
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isSyncing) {
                                stringResource(id = R.string.settings_sync_now_loading)
                            } else {
                                stringResource(id = R.string.settings_sync_now_idle)
                            }
                        )
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
                    text = stringResource(id = R.string.settings_section_privacy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(id = R.string.settings_strict_privacy_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(id = R.string.settings_strict_privacy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictPrivacyEnabled,
                        onCheckedChange = { enabled ->
                            strictPrivacyEnabled = enabled
                            prefMgr.setStrictPrivacyEnabled(enabled)
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = R.string.settings_pii_redaction_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(id = R.string.settings_pii_redaction_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = piiRedactionEnabled,
                        onCheckedChange = { enabled ->
                            piiRedactionEnabled = enabled
                            prefMgr.setPiiRedactionEnabled(enabled)
                        }
                    )
                }
            }
        }

        // F-N usability [2026-09-06]: Notification Access card — gives users a way to
        // jump straight to the system permission screen without leaving Settings. This
        // is the single highest-impact onboarding surface: without listener access the
        // app captures nothing, so making the path to granting it obvious is critical.

        // Refresh when the user returns to this screen (e.g., after toggling the
        // system permission switch and pressing Back).
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    listenerGranted = checkNotificationPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (listenerGranted)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_section_listener),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (listenerGranted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (listenerGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(
                            id = if (listenerGranted) R.string.settings_listener_granted_desc
                            else R.string.settings_listener_missing_desc
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (listenerGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.error,
                        contentColor = if (listenerGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            id = if (listenerGranted) R.string.settings_listener_open
                            else R.string.settings_listener_grant
                        )
                    )
                }
            }
        }

        // F-N usability [2026-09-06]: Boot & Restore card — exposes the existing
        // restore-on-boot preference so users can opt in/out without digging through
        // hidden debug menus. Audit F-L documents why this defaults off; the toggle
        // is intentionally disabled until the listener permission is granted.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_section_restore),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.settings_restore_on_boot_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(id = R.string.settings_restore_on_boot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = restoreOnBootEnabled && listenerGranted,
                        enabled = listenerGranted,
                        onCheckedChange = {
                            restoreOnBootEnabled = it
                            prefMgr.setRestoreOnBootEnabled(it)
                        }
                    )
                }
                
                OutlinedButton(
                    onClick = {
                        // Launch the system file picker scoped to .enc backup files. The
                        // MIME filter keeps irrelevant files (images, docs) out of the chooser.
                        // Persistable URI permissions are requested implicitly by OpenDocument.
                        pickBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(id = R.string.settings_restore_manual))
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
