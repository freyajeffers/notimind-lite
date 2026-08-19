package com.jeffers.notimindlite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.ui.screens.checkNotificationPermission
import com.jeffers.notimindlite.util.DynamicClusterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted for post notifications
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.notificationDao()
        val authManager = com.jeffers.notimindlite.data.auth.AuthManager(applicationContext)

        checkPostNotificationsPermission()

        // Asynchronously initialize dynamic semantic clusters from PackageManager
        lifecycleScope.launch(Dispatchers.IO) {
            DynamicClusterManager.initialize(applicationContext)
        }

        setContent {
            com.jeffers.notimindlite.ui.theme.NotiMindLiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showPermissionDialog by remember { mutableStateOf(false) }
                    var hasPermission by remember { mutableStateOf(checkNotificationPermission(this)) }
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val granted = checkNotificationPermission(this@MainActivity)
                                    withContext(Dispatchers.Main) {
                                        if (granted && !hasPermission) {
                                            NotificationLoggerService.rebindService(this@MainActivity)
                                        }
                                        hasPermission = granted
                                        if (granted) {
                                            showPermissionDialog = false
                                        }
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (!hasPermission) {
                            showPermissionDialog = true
                        }
                    }

                    MainNavigation(notificationDao = dao, authManager = authManager, db = database)

                    if (showPermissionDialog && !hasPermission) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            title = { Text(stringResource(id = R.string.main_listener_required_title)) },
                            text = { Text(stringResource(id = R.string.main_listener_required_desc)) },
                            confirmButton = {
                            Button(
                                onClick = {
                                    showPermissionDialog = false
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                }
                            ) {
                                Text(stringResource(id = R.string.main_grant_permission))
                            }
                            },
                            dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) {
                                Text(stringResource(id = R.string.common_later))
                            }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
