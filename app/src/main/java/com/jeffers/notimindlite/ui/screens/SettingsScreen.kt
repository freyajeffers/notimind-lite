package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.auth.UserSession
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.data.sync.SyncWorker
import com.jeffers.notimindlite.domain.backup.generateBackupKey
import com.jeffers.notimindlite.ui.components.BackupKeyDialog
import com.jeffers.notimindlite.ui.components.RestoreBackupDialog
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.SecretKey

@Composable
fun settingsScreen(
    authManager: AuthManager,
    preferencesRepository: com.jeffers.notimindlite.data.local.PreferencesRepository
) {
    val session by authManager.session.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        accountSection(
            session = session,
            authManager = authManager,
            scope = scope,
            context = context
        )

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
        notificationAccessCard(context, listenerGrantedTop)

        settingsSyncSection(preferencesRepository = preferencesRepository)

        settingsBackupSection(
            prefMgr = prefMgrTop,
            listenerGranted = listenerGrantedTop,
            snackbarHostState = snackbarHostState
        )

        // Continue with other sections
        settingsCaptureSection(preferencesRepository = preferencesRepository)
        profileManagerSection(repository = preferencesRepository.profileManager)
        settingsPreferencesSection(preferencesRepository = preferencesRepository)
        settingsPrivacySection(preferencesRepository = preferencesRepository)
        settingsAdvancedSection(
            preferencesRepository = preferencesRepository,
            onMigrationCompleted = { (context as? android.app.Activity)?.recreate() }
        )
        settingsPreferencesBackupSection()
        settingsVersionFooter(context)

        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun settingsBackupSection(
    prefMgr: PreferenceManager,
    listenerGranted: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBackupKeyDialog by remember { mutableStateOf(false) }
    var backupKeyBase64 by remember { mutableStateOf("") }
    var pendingBackupKey by remember { mutableStateOf<SecretKey?>(null) }
    val pickBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedBackupUri = uri
            showRestoreDialog = true
        }
    }
    restoreCard(
        prefMgr = prefMgr,
        listenerGranted = listenerGranted,
        onPickBackup = { pickBackupLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
        onCreateBackup = {
            if (NetworkUtils.isInternetAvailable(context)) {
                val secretKey = generateBackupKey(context)
                pendingBackupKey = secretKey
                backupKeyBase64 = com.jeffers.notimindlite.data.local.BackupKeyCodec
                    .encode(secretKey)
                showBackupKeyDialog = true
            }
        }
    )
    restoreDialogs(
        config = RestoreDialogConfig(
            context = context,
            scope = scope,
            selectedBackupUri = selectedBackupUri,
            showRestoreDialog = showRestoreDialog,
            restoreSuccessMsg = stringResource(R.string.settings_restore_success),
            restoreFailureMsg = stringResource(R.string.settings_restore_failure),
            backupKeyBase64 = backupKeyBase64,
            pendingBackupKey = pendingBackupKey,
            showBackupKeyDialog = showBackupKeyDialog,
            onRestoreDismiss = { showRestoreDialog = false },
            onBackupDismiss = {
                showBackupKeyDialog = false
                pendingBackupKey = null
            },
            snackbarHostState = snackbarHostState,
            onSyncMessage = {}
        )
    )
}

@Composable
private fun settingsVersionFooter(context: Context) {
    val versionName = remember { getVersionName(context) }
    val versionCode = remember { getVersionCode(context) }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(id = R.string.settings_version_footer, versionName, versionCode),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

private fun getVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

private fun getVersionCode(context: Context): Int =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode.toInt()
    }.getOrDefault(0)

@Composable
private fun notificationAccessCard(context: Context, listenerGranted: Boolean) {
    val cardColor = if (listenerGranted) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val listenerIcon = if (listenerGranted) {
        Icons.Default.Notifications
    } else {
        Icons.Default.NotificationsOff
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
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
                    imageVector = listenerIcon,
                    contentDescription = if (listenerGranted) {
                        "Notification access enabled"
                    } else {
                        "Notification access disabled"
                    },
                    tint = if (listenerGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = stringResource(
                        id = if (listenerGranted) {
                            R.string.settings_listener_granted_desc
                        } else {
                            R.string.settings_listener_missing_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            notificationAccessButton(context, listenerGranted)
        }
    }
}

@Composable
private fun notificationAccessButton(context: Context, listenerGranted: Boolean) {
    val containerColor = if (listenerGranted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.error
    }
    val contentColor = if (listenerGranted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onError
    }
    val actionLabel = if (listenerGranted) {
        R.string.settings_listener_open
    } else {
        R.string.settings_listener_grant
    }
    Button(
        onClick = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(stringResource(id = actionLabel))
    }
}

@Composable
private fun accountSection(
    session: UserSession,
    authManager: AuthManager,
    scope: CoroutineScope,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (session.isAuthenticated) {
                authenticatedAccount(session, authManager, context)
            } else {
                unauthenticatedAccount(session, authManager, scope, context)
            }
        }
    }
}

@Composable
private fun authenticatedAccount(
    session: UserSession,
    authManager: AuthManager,
    context: Context
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Person, contentDescription = "Profile")
        Column {
            Text(
                text = session.displayName
                    ?: stringResource(id = R.string.settings_default_display_name),
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
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
        Spacer(Modifier.width(8.dp))
        Text("Sign Out")
    }
}

@Composable
private fun unauthenticatedAccount(
    session: UserSession,
    authManager: AuthManager,
    scope: CoroutineScope,
    context: Context
) {
    Text(
        text = "Sign in with Google to enable cloud backup & multi-device sync.",
        style = MaterialTheme.typography.bodyMedium
    )
    Button(
        onClick = {
            scope.launch {
                authManager.signInWithGoogle()
                SyncWorker.schedulePeriodicSync(context)
            }
        },
        enabled = !session.isAuthenticating,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Sign in")
        Spacer(Modifier.width(8.dp))
        Text(if (session.isAuthenticating) "Signing In..." else "Sign in with Google")
    }
    session.error?.let { error ->
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
