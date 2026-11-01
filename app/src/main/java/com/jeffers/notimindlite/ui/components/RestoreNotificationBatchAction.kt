package com.jeffers.notimindlite.ui.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoredNotificationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RestoreNotificationBatchAction(
    items: List<NotificationEntity>,
    dao: NotificationDao,
    labelResource: Int
) {
    val restorableItems = remember(items) { items.filter(RestoredNotificationState::canRestore) }
    if (restorableItems.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    TextButton(
        onClick = {
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        NotificationRestoreCoordinator.restoreItems(context, dao, restorableItems)
                    }
                    Toast
                        .makeText(
                            context,
                            context.getString(
                                R.string.settings_restore_dismissed_success,
                                result.restoredCount,
                                result.queuedCount
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                } catch (_: SecurityException) {
                    showRestoreToast(context, R.string.notification_restore_permission_error)
                } catch (_: IllegalStateException) {
                    showRestoreToast(context, R.string.settings_restore_dismissed_failure)
                } catch (_: IllegalArgumentException) {
                    showRestoreToast(context, R.string.settings_restore_dismissed_failure)
                }
            }
        }
    ) {
        Icon(imageVector = Icons.Default.Restore, contentDescription = null)
        Text(stringResource(labelResource))
    }
}

private fun showRestoreToast(context: android.content.Context, messageResource: Int) {
    Toast
        .makeText(
            context,
            messageResource,
            Toast.LENGTH_LONG
        ).show()
}
