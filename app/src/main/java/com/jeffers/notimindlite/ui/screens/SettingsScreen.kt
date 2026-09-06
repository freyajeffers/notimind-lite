package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
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
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.auth.UserSession
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.jeffers.notimindlite.data.sync.SyncWorker
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.util.generateBackupKey
import kotlinx.coroutines.launch
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
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Cloud Backup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
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
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (session.isAuthenticated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                        Column {
                            Text(
                                text = session.displayName ?: "Google User",
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
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                } else {
                    Text(
                        text = "Sign in with Google to enable cloud backup & multi-device sync.",
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
                        Text(if (session.isAuthenticating) "Signing In..." else "Sign in with Google")
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
                        text = "Cloud Sync & Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Automatic sync is active. You can also trigger an instant sync manually.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = {
                            session.uid?.let { uid ->
                                scope.launch {
                                    isSyncing = true
                                    val repo = FirestoreSyncRepository(db)
                                    
                                    val secretKey: SecretKey = generateBackupKey()
                                    
                                    val res = repo.sync(uid, secretKey)
                                    isSyncing = false
                                    syncMessage = if (res.isSuccess) {
                                        "Synced ${res.getOrDefault(0)} items successfully"
                                    } else {
                                        "Sync failed: ${res.exceptionOrNull()?.localizedMessage}"
                                    }
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSyncing) "Syncing..." else "Sync Now")
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
                    text = "Privacy & Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strict Privacy Mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Disable all crash reporting and anonymous usage telemetry.",
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
            }
        }

        // F-N usability [2026-09-06]: Notification Access card — gives users a way to
        // jump straight to the system permission screen without leaving Settings. This
        // is the single highest-impact onboarding surface: without listener access the
        // app captures nothing, so making the path to granting it obvious is critical.
        val prefMgr = remember { PreferenceManager(context) }
        val lifecycleOwner = LocalLifecycleOwner.current
        var listenerGranted by remember {
            mutableStateOf(checkNotificationPermission(context))
        }
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
                    )
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
                        checked = prefMgr.isRestoreOnBootEnabled() && listenerGranted,
                        enabled = listenerGranted,
                        onCheckedChange = { prefMgr.setRestoreOnBootEnabled(it) }
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION") info.versionCode
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
    }
}
