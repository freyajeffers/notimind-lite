package com.jeffers.notimindlite.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that listens for posted and removed notifications.
 * It extracts a minimal set of fields and persists them in the Room database.
 */
class NotificationLoggerService : NotificationListenerService() {
    private val TAG = "NotificationLoggerSrv"
    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val dao by lazy { db.notificationDao() }
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val DEBOUNCE_MS = 5000L
        private val recentLogs = ConcurrentHashMap<String, Long>()
        private var instance: NotificationLoggerService? = null

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
                Log.e("NotificationLoggerSrv", "Failed to cancel notification with key: $key", e)
            }
        }

        fun rebindService(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    requestRebind(ComponentName(context, NotificationLoggerService::class.java))
                }
            } catch (e: Exception) {
                Log.e("NotificationLoggerSrv", "Failed to rebind notification listener service", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "NotificationListener connected. Syncing all active status bar notifications...")
        scope.launch {
            try {
                val activeNotifs = activeNotifications ?: emptyArray()
                val activeKeys = activeNotifs.map { it.key }.toSet()

                // Process/update all currently active notifications
                for (sbn in activeNotifs) {
                    processNotification(sbn)
                }

                // Reconcile database: mark any notification previously marked as active as dismissed if no longer present
                val dbActive = dao.getActiveNotificationsList()
                for (entity in dbActive) {
                    if (!activeKeys.contains(entity.key)) {
                        dao.markDismissed(entity.key)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing active notifications on listener connected", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return
        try {
            val notification = sbn.notification ?: return
            val key = sbn.key
            com.jeffers.notimindlite.util.NotificationLauncher.registerPendingIntent(key, notification.contentIntent)
            val extras = notification.extras
            val packageName = sbn.packageName
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: extras?.getString("android.title")
                ?: ""
            val content = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getString("android.text")
                ?: ""
            val subText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
            val bigText = extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()

            // 5-second debouncing check
            val debounceContentKey = "$packageName|$title|$content"
            val now = System.currentTimeMillis()
            val lastKeyTime = recentLogs[key] ?: 0L
            val lastContentTime = recentLogs[debounceContentKey] ?: 0L

            if ((now - lastKeyTime < DEBOUNCE_MS) || (now - lastContentTime < DEBOUNCE_MS)) {
                Log.d(TAG, "Debouncing notification update for $key / $debounceContentKey within 5s window. Skipping log.")
                return
            }
            recentLogs[key] = now
            recentLogs[debounceContentKey] = now

            val category = notification.category
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification.channelId
            } else {
                null
            }
            val priority = notification.priority
            val groupKey = sbn.groupKey
            val isOngoing = sbn.isOngoing
            val isClearable = sbn.isClearable
            val actions = notification.actions
            val actionsCount = actions?.size ?: 0
            val postTime = sbn.postTime

            // Extract action button titles and register their PendingIntents
            val actionLabelsJson: String? = if (actions != null && actions.isNotEmpty()) {
                val labels = mutableListOf<String>()
                actions.forEachIndexed { index, action ->
                    val label = action.title?.toString() ?: "Action $index"
                    labels.add(label)
                    com.jeffers.notimindlite.util.NotificationLauncher.registerActionIntent(key, index, action.actionIntent)
                }
                org.json.JSONArray(labels).toString()
            } else {
                null
            }

            val intentUri = try {
                packageManager.getLaunchIntentForPackage(packageName)?.toUri(android.content.Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                null
            }

            val entity = NotificationEntity(
                key = key,
                packageName = packageName,
                appName = appName,
                title = title,
                content = content,
                postTime = postTime,
                isDismissed = false,
                isPersistent = isOngoing,
                category = category,
                channelId = channelId,
                subText = subText,
                bigText = bigText,
                priority = priority,
                groupKey = groupKey,
                isOngoing = isOngoing,
                isClearable = isClearable,
                actionsCount = actionsCount,
                intentUri = intentUri,
                actionLabels = actionLabelsJson
            )
            scope.launch { dao.insertNotification(entity) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        handleNotificationRemoved(sbn, null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        handleNotificationRemoved(sbn, reason)
    }

    private fun handleNotificationRemoved(sbn: StatusBarNotification, reason: Int?) {
        if (sbn.packageName == applicationContext.packageName) return
        Log.d(TAG, "Notification removed: ${sbn.key}, reason: $reason. Marking as isDismissed = 1")
        val dismissTime = System.currentTimeMillis()
        scope.launch {
            if (reason != null) {
                dao.markDismissedWithReason(sbn.key, reason, dismissTime)
            } else {
                dao.markDismissed(sbn.key, dismissTime)
            }
        }
    }
}
