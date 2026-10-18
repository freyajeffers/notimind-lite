package com.jeffers.notimindlite.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.Converters
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.NotificationActionExecutor
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.util.VectorEmbeddingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Service that listens for posted and removed notifications.
 * It applies extended ingestion filters to eliminate clutter and persists
 * clean notifications in the Room database.
 */
class NotificationLoggerService : NotificationListenerService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val actionExecutor by lazy { NotificationActionExecutor(PreferencesRepository(applicationContext)) }
    private val embeddingIndexer by lazy {
        com.jeffers.notimindlite.util
            .NotificationEmbeddingIndexer()
    }

    companion object {
        private const val TAG = "NotificationLoggerSrv"
        private const val MAX_CACHE_CAPACITY = 500
        private const val CACHE_INITIAL_CAPACITY = 16
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val ICON_COMPRESSION_QUALITY = 100

        private val recentLogs: MutableMap<String, Long> = Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Long>?
                ): Boolean = size > MAX_CACHE_CAPACITY
            }
        )

        private val recentContents: MutableMap<String, String> = Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, String>?
                ): Boolean = size > MAX_CACHE_CAPACITY
            }
        )

        @Volatile
        private var instance: NotificationLoggerService? = null

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: SecurityException) {
                Log.w("NotificationLoggerSrv", "Permission denied cancelling notification: $key", e)
            } catch (e: IllegalArgumentException) {
                Log.w("NotificationLoggerSrv", "Invalid notification key when cancelling: $key", e)
            }
        }

        fun rebindService(context: Context) {
            try {
                requestRebind(ComponentName(context, NotificationLoggerService::class.java))
            } catch (e: SecurityException) {
                Log.w("NotificationLoggerSrv", "Permission denied when rebinding notification listener service", e)
            } catch (e: IllegalArgumentException) {
                Log.w("NotificationLoggerSrv", "Invalid component when rebinding notification listener service", e)
            }
        }

        suspend fun markNotificationAsRead(context: Context, key: String) {
            val preferences = PreferencesRepository(context)
            AppDatabase
                .getDatabase(context)
                .notificationDao()
                .markAsRead(key, preferences.autoDeleteOnRead.value)
        }

        suspend fun markNotificationsAsRead(context: Context, keys: List<String>) {
            val preferences = PreferencesRepository(context)
            AppDatabase
                .getDatabase(context)
                .notificationDao()
                .markAsReadBatch(keys, preferences.autoDeleteOnRead.value)
        }

        suspend fun markAllNotificationsAsRead(context: Context) {
            val preferences = PreferencesRepository(context)
            AppDatabase
                .getDatabase(context)
                .notificationDao()
                .markAllAsRead(preferences.autoDeleteOnRead.value)
        }
    }

    // Icon capture is best-effort; failures return null and callers fall back to no icon.
    private fun getOrSaveAppIconUri(packageName: String): String? {
        val iconsDir = File(cacheDir, "app_icons")
        if (!iconsDir.exists()) iconsDir.mkdirs()
        val iconFile = File(iconsDir, "$packageName.png")
        if (iconFile.exists() && iconFile.length() > 0) return iconFile.absolutePath

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            val bitmap = when (drawable) {
                is BitmapDrawable -> {
                    drawable.bitmap
                }

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
                bitmap.compress(Bitmap.CompressFormat.PNG, ICON_COMPRESSION_QUALITY, out)
            }
            iconFile.absolutePath
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            Log.w(TAG, "Application package unavailable for package=$packageName", e)
            null
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "Icon file unavailable for package=$packageName", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Icon access denied for package=$packageName", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "Icon write failed for package=$packageName", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid icon data for package=$packageName", e)
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
                Log.d(TAG, "onListenerConnected: processing ${activeNotifs.size} active notifications in batch")
                val entities = activeNotifs.mapNotNull { extractNotificationEntity(it) }
                if (entities.isNotEmpty()) {
                    val dao = database().notificationDao()
                    val ids = dao.insertNotifications(entities)
                    // F-G fix [2026-09-02 audit]: populate embeddings inline so the
                    // HybridSearchEngine (read-path) can rank new notifications
                    // semantically. Previously only DatabaseMigrator (one-shot v18
                    // backfill) wrote embeddings, leaving every post-migration
                    // notification unrankable. Pure-Kotlin compute, sub-ms per
                    // embedding, LRU-cached (256 entries), safe on the IO scope.
                    embeddingIndexer.indexBatch(dao, ids, entities)
                    Log.d(TAG, "onListenerConnected: successfully batch inserted ${entities.size} active notifications")
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Invalid database state syncing active notifications", e)
            } catch (e: android.database.sqlite.SQLiteException) {
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
        if (!BuildConfig.DEBUG && !isCaptureEnabled()) {
            Log.d(TAG, "Ignoring notification post event: capture disabled")
            return
        }
        if (!isNotificationListenerActive()) {
            Log.w(TAG, "Ignoring notification post event: listener permission revoked")
            return
        }
        super.onNotificationPosted(sbn)
        Log.d(TAG, "onNotificationPosted: ${sbn.packageName} - ${sbn.id}")
        RestoredNotificationManager.onOriginalAppNotificationPosted(applicationContext, sbn.packageName)

        // Use the new extraction helper and individual insert for single posted notifications
        // (Single inserts are already optimized via Room, but we maintain compatibility)
        val entity = extractNotificationEntity(sbn)
        if (entity != null) {
            registerNotificationActions(sbn, entity.key)
            actionExecutor.executeOnNotification(entity.key, entity.packageName, entity.title)
            scope.launch {
                try {
                    val dao = database().notificationDao()
                    val existing = dao.getNotificationByKey(entity.key)

                    // Logic: Only treat as a "new notification" (new row) if the content has changed significantly.
                    // Significant change = title or content is different.
                    // Otherwise, update the existing row (increment update count).
                    val hasSignificantChange = existing == null ||
                        existing.title != entity.title ||
                        existing.content != entity.content

                    val updateCount = if (hasSignificantChange) 1 else existing.updateCount + 1
                    val originalPostTime = if (hasSignificantChange) entity.postTime else existing.postTime

                    val finalEntity = entity.copy(
                        id = if (hasSignificantChange) 0L else existing.id,
                        updateCount = updateCount,
                        postTime = originalPostTime,
                        isRead = existing?.isRead ?: false,
                        isPinned = existing?.isPinned ?: false
                    )
                    dao.insert(finalEntity)
                    // F-G fix [2026-09-02 audit]: populate embedding for the single
                    // posted notification so semantic search can rank it.
                    embeddingIndexer.indexSingle(dao, finalEntity)
                    Log.d(TAG, "Inserted: ${finalEntity.title} (${finalEntity.appName})")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Invalid database state inserting ${entity.title}", e)
                } catch (e: android.database.sqlite.SQLiteException) {
                    Log.e(TAG, "DB insert failed for ${entity.title}", e)
                }
            }
        }
    }

    private fun isNotificationListenerActive(): Boolean {
        val componentName = ComponentName(applicationContext, NotificationLoggerService::class.java)
        val enabledListeners = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        // `enabled_notification_listeners` is a colon-separated list of components that the
        // system stores in either fully-qualified form (`pkg/pkg.Cls`) or package-shorthand
        // form (`pkg/.Cls`). Compare against both to remain robust to whichever format the
        // current Settings provider used when our entry was written.
        val short = componentName.flattenToShortString()
        val long = componentName.flattenToString()
        return enabledListeners.split(':').any { it == short || it == long }
    }

    @Suppress(
        "ReturnCount", "LongMethod", "CyclomaticComplexMethod"
    ) // PackageManager lookups (app label, launch intent) intentionally return null on any failure so
    // extraction stays resilient across OEM variants; this method has 10 returns because each guard
    // returns early (blank content, summary, stale, missing app, etc.) — the function is intentionally
    // structured as a filter pipeline. Splitting it is tracked as future work; current contract is
    // well-covered by NotificationLoggerServiceTest.
    private fun shouldCaptureNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == applicationContext.packageName) return false
        val preferences = com.jeffers.notimindlite.data.local
            .PreferencesRepository(applicationContext)
        if (!preferences.captureManager.ongoing.value && sbn.isOngoing) return false
        val notification = sbn.notification ?: return false
        if (preferences.captureManager.actionsOnly.value && notification.actions.isNullOrEmpty()) return false
        val allow = parseNotificationPackageList(preferences.captureManager.allowlist.value)
        val block = parseNotificationPackageList(preferences.captureManager.blocklist.value)
        if (allow.isNotEmpty() && sbn.packageName !in allow) return false
        if (sbn.packageName in block) return false
        val importance = notification.channelId?.let {
            getSystemService(android.app.NotificationManager::class.java)
                ?.getNotificationChannel(it)
                ?.importance
        } ?: android.app.NotificationManager.IMPORTANCE_DEFAULT
        return importance >= preferences.captureManager.minImportance.value
    }

    private fun extractNotificationEntity(sbn: StatusBarNotification): NotificationEntity? {
        val shouldSkip = !shouldCaptureNotification(sbn) || sbn.packageName == applicationContext.packageName
        val notification = sbn.notification
        return if (shouldSkip || notification == null) {
            null
        } else {
            try {
                com.jeffers.notimindlite.util
                    .NotificationSanitizer(
                        applicationContext,
                        ::getOrSaveAppIconUri
                    ).sanitizeAndBuild(sbn, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to extract notification entity - security", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to extract notification entity - illegal argument", e)
                null
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to extract notification entity - illegal state", e)
                null
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isNotificationListenerActive()) {
            Log.w(TAG, "Ignoring notification remove event: listener permission revoked")
            return
        }
        super.onNotificationRemoved(sbn)
        handleNotificationRemoved(sbn, null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (!isNotificationListenerActive()) {
            Log.w(TAG, "Ignoring notification remove event: listener permission revoked")
            return
        }
        super.onNotificationRemoved(sbn, rankingMap, reason)
        handleNotificationRemoved(sbn, reason)
    }

    private fun handleNotificationRemoved(sbn: StatusBarNotification, reason: Int?) {
        if (sbn.packageName == applicationContext.packageName) return
        // Always use a deterministic app-derived key. The system-provided sbn.key is
        // inconsistent between post/remove events on some devices, causing dismissal
        // lookups to miss the originally logged row.
        val key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        Log.d(TAG, "Notification removed: $key, reason: $reason")
        NotificationLauncher.unregisterPendingIntent(key)
        recentLogs.remove(key)
        recentContents.remove(key)
        val effectiveReason = reason ?: 1

        scope.launch {
            try {
                val dao = database().notificationDao()
                dao.markDismissedWithReasonByMatching(
                    key = key,
                    packageName = sbn.packageName,
                    title = title,
                    content = content,
                    reason = effectiveReason
                )
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Invalid database state marking notification dismissed for $key", e)
            } catch (e: android.database.sqlite.SQLiteException) {
                Log.e(TAG, "Database error marking notification dismissed for $key", e)
            }
        }
    }
}
