package com.jeffers.notimindlite.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.NotificationLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Service that listens for posted and removed notifications.
 * It applies extended ingestion filters to eliminate clutter and persists
 * clean notifications in the Room database.
 */
class NotificationLoggerService : NotificationListenerService() {
    private val TAG = "NotificationLoggerSrv"

    private fun getDb(): AppDatabase = AppDatabase.getDatabase(applicationContext)
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val DEBOUNCE_MS = 30000L
        private const val MAX_CACHE_CAPACITY = 500

        private val recentLogs: MutableMap<String, Long> = Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                    return size > MAX_CACHE_CAPACITY
                }
            }
        )

        private val recentContents: MutableMap<String, String> = Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                    return size > MAX_CACHE_CAPACITY
                }
            }
        )

        @Volatile
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
                requestRebind(ComponentName(context, NotificationLoggerService::class.java))
            } catch (e: Exception) {
                Log.e("NotificationLoggerSrv", "Failed to rebind notification listener service", e)
            }
        }
    }

    private fun getOrSaveAppIconUri(packageName: String): String? {
        val iconsDir = File(cacheDir, "app_icons")
        if (!iconsDir.exists()) iconsDir.mkdirs()
        val iconFile = File(iconsDir, "$packageName.png")
        if (iconFile.exists() && iconFile.length() > 0) return iconFile.absolutePath

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            iconFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "onListenerConnected: listener registered successfully")

        scope.launch {
            try {
                val activeNotifs = activeNotifications ?: emptyArray()
                Log.d(TAG, "onListenerConnected: processing ${activeNotifs.size} active notifications")
                for (sbn in activeNotifs) {
                    processNotification(sbn)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing active notifications on listener connected", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "onNotificationPosted: ${sbn.packageName} - ${sbn.id}")
        RestoredNotificationManager.onOriginalAppNotificationPosted(applicationContext, sbn.packageName)
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return
        try {
            val notification = sbn.notification ?: return
            val packageName = sbn.packageName

            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            val inboxLinesJson: String? = if (!textLines.isNullOrEmpty()) {
                val linesList = textLines.map { it.toString() }
                JSONArray(linesList).toString()
            } else {
                null
            }

            val category = notification.category
            @Suppress("DEPRECATION")
            val priority = notification.priority
            val postTime = sbn.postTime
            val now = System.currentTimeMillis()

            // ════════════════════════════════════════════════════════════
            // ── Ingestion Filters ──
            // ════════════════════════════════════════════════════════════

            // 1. Package Blacklist
            val blacklistedPackages = setOf(
                "com.android.shell",
                "com.google.android.googlequicksearchbox"
            )
            if (packageName in blacklistedPackages) {
                Log.d(TAG, "IngestionFilter: Blacklisted package $packageName skipped")
                return
            }

            // 2. System Status & Debugging Clutter Filter
            if ((packageName == "android" || packageName == "com.android.systemui") &&
                (priority <= -2 || category == Notification.CATEGORY_SERVICE || category == Notification.CATEGORY_SYSTEM || category == "sys") &&
                (title.contains("USB", ignoreCase = true) || title.contains("debugging", ignoreCase = true) || title.contains("charging", ignoreCase = true))
            ) {
                Log.d(TAG, "IngestionFilter: System USB/debugging/charging clutter filtered: $packageName - $title")
                return
            }

            // 3. Low-Value Category Filter (only for system packages)
            if ((packageName == "android" || packageName == "com.android.systemui") &&
                (category == Notification.CATEGORY_SERVICE || category == Notification.CATEGORY_SYSTEM)
            ) {
                Log.d(TAG, "IngestionFilter: System service/system category clutter filtered: $packageName - $category")
                return
            }

            // 4. Blank Title and Content Filter
            if (title.isBlank() && content.isBlank()) {
                Log.d(TAG, "IngestionFilter: Blank title and content notification clutter skipped from $packageName")
                return
            }

            // 5. Summary Noise Filter ("3 more notifications")
            val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
            if (summaryRegex.matches(title) || summaryRegex.matches(content) || (subText != null && summaryRegex.matches(subText))) {
                Log.d(TAG, "IngestionFilter: Summary clutter filtered from $packageName: $title")
                return
            }

            // 6. Age Retention Filter (skip notifications older than 30 days)
            val maxAgeMs = 30L * 24 * 60 * 60 * 1000L
            if (postTime > 0 && (now - postTime) > maxAgeMs) {
                Log.d(TAG, "IngestionFilter: Notification older than 30 days skipped ($packageName)")
                return
            }

            val key = sbn.key ?: "${sbn.id}|${sbn.packageName}|${sbn.postTime}"
            NotificationLauncher.registerPendingIntent(key, notification.contentIntent)

            // ── Dynamic 30s Debounce ──
            val contentSignature = "$title|$content"
            val lastLogTime = recentLogs[key] ?: 0L
            val lastContent = recentContents[key]
            if (lastContent == contentSignature && (now - lastLogTime < DEBOUNCE_MS)) {
                Log.d(TAG, "Smart Debounce: Duplicate update within 30s window for $key")
                return
            }
            recentLogs[key] = now
            recentContents[key] = contentSignature

            val rawAppName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
            }
            val appName = rawAppName ?: packageName
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val smallIconRes = notification.smallIcon?.resId ?: 0
            val appIconUri = getOrSaveAppIconUri(packageName)

            val channelId = notification.channelId
            val groupKey = sbn.groupKey
            val isOngoing = sbn.isOngoing
            val isClearable = sbn.isClearable
            val actions = notification.actions
            val actionsCount = actions?.size ?: 0

            val actionLabelsJson: String? = if (!actions.isNullOrEmpty()) {
                val labels = mutableListOf<String>()
                actions.forEachIndexed { index, action ->
                    val label = action.title?.toString() ?: "Action $index"
                    labels.add(label)
                    NotificationLauncher.registerActionIntent(key, index, action.actionIntent)
                }
                JSONArray(labels).toString()
            } else {
                null
            }

            val intentUri = try {
                packageManager.getLaunchIntentForPackage(packageName)?.toUri(Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                null
            }

            scope.launch {
                try {
                    val dao = getDb().notificationDao()
                    val existing = dao.getNotificationByKey(key)
                    val updateCount = (existing?.updateCount ?: 0) + 1
                    val originalPostTime = if (existing != null && existing.postTime > 0) existing.postTime else postTime

                    val entity = NotificationEntity(
                        id = existing?.id ?: 0L,
                        key = key,
                        packageName = packageName,
                        appName = appName,
                        appIconUri = appIconUri ?: existing?.appIconUri,
                        title = title,
                        content = content,
                        postTime = originalPostTime,
                        lastUpdatedTime = now,
                        updateCount = updateCount,
                        isDismissed = false,
                        isPersistent = isOngoing,
                        isRead = existing?.isRead ?: false,
                        isGroupSummary = isGroupSummary,
                        category = category,
                        channelId = channelId,
                        subText = subText,
                        bigText = bigText,
                        inboxLinesJson = inboxLinesJson,
                        priority = priority,
                        groupKey = groupKey,
                        isOngoing = isOngoing,
                        isClearable = isClearable,
                        actionsCount = actionsCount,
                        intentUri = intentUri,
                        isPinned = existing?.isPinned ?: false,
                        actionLabels = actionLabelsJson,
                        smallIconRes = smallIconRes
                    )
                    dao.insert(entity)
                    Log.d(TAG, "Inserted: $title ($appName)")
                } catch (e: Exception) {
                    Log.e(TAG, "DB insert failed for $title", e)
                }
            }
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
        val key = sbn.key ?: "${sbn.id}|${sbn.packageName}|${sbn.postTime}"
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        Log.d(TAG, "Notification removed: $key, reason: $reason")
        NotificationLauncher.unregisterPendingIntent(key)
        recentLogs.remove(key)
        recentContents.remove(key)
        val dismissTime = System.currentTimeMillis()
        val effectiveReason = reason ?: 1

        scope.launch {
            try {
                val dao = getDb().notificationDao()
                dao.markDismissedWithReasonByMatching(key, sbn.packageName, title, content, effectiveReason, dismissTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark notification dismissed for $key", e)
            }
        }
    }
}
