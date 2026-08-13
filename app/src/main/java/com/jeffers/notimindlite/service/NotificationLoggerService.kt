package com.jeffers.notimindlite.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.AppEntity
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Service that listens for posted and removed notifications.
 * It extracts a minimal set of fields and persists them in the Room database,
 * with full Direct Boot (pre-PIN and post-PIN) storage routing.
 */
class NotificationLoggerService : NotificationListenerService() {
    private val TAG = "NotificationLoggerSrv"

    private fun getDb(): AppDatabase = AppDatabase.getDatabase(applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val DEBOUNCE_MS = 30000L // 30-second dynamic debounce
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
                val currentDao = getDb().notificationDao()
                val dbActive = currentDao.getActiveNotificationsList()
                for (entity in dbActive) {
                    if (!activeKeys.contains(entity.key)) {
                        currentDao.markDismissed(entity.key)
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

    private fun isListenerGranted(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        if (enabledListeners.isNullOrEmpty()) return true
        val myComponent = ComponentName(this, NotificationLoggerService::class.java).flattenToString()
        val myComponentShort = ComponentName(this, NotificationLoggerService::class.java).flattenToShortString()
        return enabledListeners.contains(myComponent) || enabledListeners.contains(myComponentShort)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return
        if (!isListenerGranted()) {
            Log.d(TAG, "Notification listener permission is not granted. Skipping notification processing.")
            return
        }
        try {
            val notification = sbn.notification ?: return
            val packageName = sbn.packageName

            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras?.getString("android.title")
                ?: ""
            val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getString("android.text")
                ?: ""
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            // Extract EXTRA_TEXT_LINES for InboxStyle notifications
            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            val inboxLinesJson: String? = if (!textLines.isNullOrEmpty()) {
                val linesList = textLines.map { it.toString() }
                JSONArray(linesList).toString()
            } else {
                null
            }

            val category = notification.category
            val priority = notification.priority
            val postTime = sbn.postTime
            val now = System.currentTimeMillis()

            // Group summary flag check
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val smallIconRes = notification.smallIcon?.resId ?: 0

            // Cache status bar app icon image
            val appIconUri = getOrSaveAppIconUri(packageName)

            // ── Extended Filters ──

            // 1. Package-based Black-list
            val blacklistedPackages = setOf(
                "android",
                "com.android.systemui",
                "com.android.shell",
                "com.google.android.googlequicksearchbox"
            )
            if (packageName in blacklistedPackages) {
                Log.d(TAG, "Black-list filter – skipping $packageName")
                return
            }

            // 2. Age-based retention filter (30 days)
            val maxAgeMs = 30L * 24 * 60 * 60 * 1000 // 30 days
            val ageMs = now - postTime
            if (postTime > 0 && ageMs > maxAgeMs) {
                Log.d(TAG, "Age filter – skipping notification older than 30 days")
                return
            }

            // 3. Spam-risk keyword filter
            val spamKeywords = listOf("spam", "spam risk", "blocked", "risk")
            if (spamKeywords.any { title.contains(it, ignoreCase = true) || content.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "Spam filter – skipping $title")
                return
            }

            // 4. Summary / empty filter
            val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
            if (summaryRegex.matches(title) || (title.isBlank() && content.isBlank())) {
                Log.d(TAG, "Summary/empty filter – skipping $title")
                return
            }

            // 5. Low-value system category filter
            val lowValueCategories = setOf(Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM)
            if (category in lowValueCategories) {
                Log.d(TAG, "Category filter – skipping $category")
                return
            }

            val key = sbn.key
            com.jeffers.notimindlite.util.NotificationLauncher.registerPendingIntent(key, notification.contentIntent)

            // ── Smart & Dynamic 30s Debounce ──
            val contentSignature = "$title|$content"
            val lastLogTime = recentLogs[key] ?: 0L
            val lastContent = recentContents[key]

            // If identical title & content received within 30s window, debounce it
            if (lastContent == contentSignature && (now - lastLogTime < DEBOUNCE_MS)) {
                Log.d(TAG, "Smart Debounce: Identical notification update for $key within 30s window. Skipping log.")
                return
            }

            // Update debounce cache
            recentLogs[key] = now
            recentContents[key] = contentSignature

            val rawAppName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
            }

            // Format clean user-facing app name if label is missing or is raw package name
            val appName = if (!rawAppName.isNullOrBlank() && !rawAppName.contains(".")) {
                rawAppName
            } else {
                val parts = packageName.split(".")
                val lastPart = parts.lastOrNull { part ->
                    part != "android" && part != "app" && part != "apps" && part != "mobile" && part != "lite"
                } ?: parts.lastOrNull() ?: packageName
                val formatted = lastPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                formatted.ifBlank { packageName.ifBlank { "Unknown App" } }
            }

            val channelId = notification.channelId
            val groupKey = sbn.groupKey
            val isOngoing = sbn.isOngoing
            val isClearable = sbn.isClearable
            val actions = notification.actions
            val actionsCount = actions?.size ?: 0

            // Extract action button titles and register their PendingIntents
            val actionLabelsJson: String? = if (actions != null && actions.isNotEmpty()) {
                val labels = mutableListOf<String>()
                actions.forEachIndexed { index, action ->
                    val label = action.title?.toString() ?: "Action $index"
                    labels.add(label)
                    com.jeffers.notimindlite.util.NotificationLauncher.registerActionIntent(key, index, action.actionIntent)
                }
                JSONArray(labels).toString()
            } else {
                null
            }

            val intentUri = try {
                packageManager.getLaunchIntentForPackage(packageName)?.toUri(android.content.Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                null
            }

            // Determine deduplication key: prefer existing key, fallback to hash of packageName, title, and content
            val dedupKey = if (key.isNotBlank()) key else "${packageName}_${title}_${content}".hashCode().toString()

            scope.launch {
                val db = getDb()
                val appDao = db.appDao()
                val dao = db.notificationDao()

                // Upsert app metadata in normalized apps table
                val existingApp = appDao.getAppByPackage(packageName)
                val firstSeen = existingApp?.firstSeenTime ?: now
                appDao.insertOrUpdateApp(
                    AppEntity(
                        packageName = packageName,
                        appName = appName,
                        appIconUri = appIconUri ?: existingApp?.appIconUri,
                        statusBarIconRes = smallIconRes,
                        statusBarIconPackage = packageName,
                        firstSeenTime = firstSeen,
                        lastSeenTime = now
                    )
                )

                val existing = dao.getNotificationByKey(dedupKey)
                val updateCount = (existing?.updateCount ?: 0) + 1
                val originalPostTime = if (existing != null && existing.postTime > 0) existing.postTime else postTime

                val entity = NotificationEntity(
                    id = existing?.id ?: 0,
                    key = dedupKey,
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
                dao.insertNotification(entity)
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
        Log.d(TAG, "Notification removed: ${sbn.key}, reason: $reason. Marking as isDismissed = 1")
        com.jeffers.notimindlite.util.NotificationLauncher.unregisterPendingIntent(sbn.key)
        recentLogs.remove(sbn.key)
        recentContents.remove(sbn.key)
        val dismissTime = System.currentTimeMillis()

        val notification = sbn.notification
        val extras = notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras?.getString("android.title")
            ?: ""
        val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getString("android.text")
            ?: ""

        scope.launch {
            val dao = getDb().notificationDao()
            if (sbn.key.isNotBlank()) {
                if (reason != null) {
                    dao.markDismissedWithReason(sbn.key, reason, dismissTime)
                } else {
                    dao.markDismissed(sbn.key, dismissTime)
                }
            } else {
                val sbnKey = "${sbn.packageName}_${title}_${content}".hashCode().toString()
                if (reason != null) {
                    dao.markDismissedWithReasonByMatching(sbnKey, sbn.packageName, title, content, reason, dismissTime)
                } else {
                    dao.markDismissedByMatching(sbnKey, sbn.packageName, title, content, dismissTime)
                }
            }
        }
    }
}
