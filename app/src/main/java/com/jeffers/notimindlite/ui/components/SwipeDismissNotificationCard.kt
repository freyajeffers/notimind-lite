package com.jeffers.notimindlite.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.jeffers.notimindlite.service.NotificationRestoreCoordinator
import com.jeffers.notimindlite.service.RestoredNotificationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SwipeDismissNotificationCard(
    item: NotificationEntity,
    dao: NotificationDao,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val restored = RestoredNotificationState.notificationId(item.restoreMetadataJson) != null
    val queued = RestoredNotificationState.isQueued(item.restoreMetadataJson)
    val enabled = !item.isDismissed || restored || queued

    SwipeDismissibleCard(
        enabled = enabled,
        onDismiss = {
            try {
                val dismissed = withContext(Dispatchers.IO) {
                    when {
                        restored -> {
                            NotificationRestoreCoordinator.dismiss(context, dao, item)
                        }

                        queued -> {
                            NotificationRestoreCoordinator.cancelQueued(dao, item)
                        }

                        else -> {
                            if (!NotificationLoggerService.dismissNotificationFromApp(item.key)) {
                                false
                            } else {
                                dao.markDismissedWithReason(item.key, APP_SWIPE_DISMISS_REASON)
                                true
                            }
                        }
                    }
                }
                if (!dismissed) showDismissFailure(context)
                dismissed
            } catch (_: SecurityException) {
                showDismissFailure(context)
                false
            } catch (_: IllegalStateException) {
                showDismissFailure(context)
                false
            } catch (_: IllegalArgumentException) {
                showDismissFailure(context)
                false
            }
        },
        content = content
    )
}

private const val APP_SWIPE_DISMISS_REASON = 2

private fun showDismissFailure(context: Context) {
    Toast
        .makeText(
            context,
            R.string.notification_dismiss_failure,
            Toast.LENGTH_LONG
        ).show()
}
