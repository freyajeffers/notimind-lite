package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.data.auth.AuthManager
import com.jeffers.notimindlite.data.auth.UserSession
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.jeffers.notimindlite.data.sync.SyncWorker
import com.jeffers.notimindlite.data.local.PreferenceManager
import com.jeffers.notimindlite.util.TelemetryManager
import kotlinx.coroutines.launch

@Composable
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
                                    val res = repo.sync(uid)
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
                            TelemetryManager.setPrivacyMode(enabled)
                        }
                    )
                }
            }
        }
    }
}
