package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RestoreRecentlyDismissedCard(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(configuration) { context.resources }
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf(false) }
    val emptyMessage = stringResource(R.string.settings_restore_dismissed_none)
    val failureMessage = stringResource(R.string.settings_restore_dismissed_failure)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_restore_dismissed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_restore_dismissed_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        if (restoring) return@launch
                        restoring = true
                        try {
                            val restoredCount = withContext(Dispatchers.IO) {
                                val dao = AppDatabase.getDatabase(context.applicationContext).notificationDao()
                                NotificationRestoreCoordinator.restoreRecentlyDismissed(context, dao)
                            }
                            snackbarHostState.showSnackbar(
                                if (restoredCount.restoredCount == 0 && restoredCount.queuedCount == 0) {
                                    emptyMessage
                                } else {
                                    resources.getString(
                                        R.string.settings_restore_dismissed_success,
                                        restoredCount.restoredCount,
                                        restoredCount.queuedCount
                                    )
                                }
                            )
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(failureMessage)
                        } finally {
                            restoring = false
                        }
                    }
                },
                enabled = !restoring,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(
                            text = stringResource(R.string.settings_restore_dismissed_action),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
