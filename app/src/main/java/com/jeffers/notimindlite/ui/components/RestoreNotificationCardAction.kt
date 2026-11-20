package com.jeffers.notimindlite.ui.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoreResult
import com.jeffers.notimindlite.service.RestoredNotificationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RestoreNotificationCardAction(item: NotificationEntity, dao: NotificationDao) {
    val restored = RestoredNotificationState.notificationId(item.restoreMetadataJson) != null
    val queued = RestoredNotificationState.isQueued(item.restoreMetadataJson)
    val canRestore = RestoredNotificationState.canRestore(item)
    if (!restored && !queued && !canRestore) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actionLabel = stringResource(
        id = when {
            restored -> R.string.a11y_dismiss_restored_for
            queued -> R.string.a11y_remove_restore_for
            else -> R.string.a11y_restore_for
        },
        item.title,
        item.appName
    )
    TextButton(
        onClick = {
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        if (restored) {
                            NotificationRestoreCoordinator.dismiss(context, dao, item)
                            RestoreResult.SKIPPED
                        } else if (queued) {
                            NotificationRestoreCoordinator.cancelQueued(dao, item)
                            RestoreResult.SKIPPED
                        } else {
                            NotificationRestoreCoordinator.restore(context, dao, item)
                        }
                    }
                    if (result == RestoreResult.QUEUED) {
                        Toast
                            .makeText(
                                context,
                                R.string.notification_restore_queued,
                                Toast.LENGTH_LONG
                            ).show()
                    }
                } catch (_: SecurityException) {
                    Toast
                        .makeText(
                            context,
                            R.string.notification_restore_permission_error,
                            Toast.LENGTH_LONG
                        ).show()
                } catch (_: IllegalStateException) {
                    Toast
                        .makeText(
                            context,
                            R.string.settings_restore_dismissed_failure,
                            Toast.LENGTH_LONG
                        ).show()
                } catch (_: IllegalArgumentException) {
                    Toast
                        .makeText(
                            context,
                            R.string.settings_restore_dismissed_failure,
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }
    ) {
        if (!restored && !queued) {
            Icon(imageVector = Icons.Default.Restore, contentDescription = null)
        }
        Text(actionLabel)
    }
}
