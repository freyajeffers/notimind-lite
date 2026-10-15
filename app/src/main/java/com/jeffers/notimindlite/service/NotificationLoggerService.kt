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
import com.jeffers.notimindlite.BuildConfig
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.Converters
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.util.NotificationLauncher
import com.jeffers.notimindlite.util.NotificationActionExecutor
import com.jeffers.notimindlite.util.VectorEmbeddingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import android.os.Bundle
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Service that listens for posted and removed notifications.
 * It applies extended ingestion filters to eliminate clutter and persists
 * clean notifications in the Room database.
 */
@Suppress("TooManyFunctions") // NotificationLoggerService is intentionally one service surface; the 12
// functions map 1:1 to NotificationListenerService lifecycle hooks + capture pipeline. Splitting
// across files would fragment directBootAware service registration. See BootRestoreManager for the
// boot-restore subset already extracted.
class NotificationLoggerService : NotificationListenerService() {
    private val TAG = "NotificationLoggerSrv"

    private fun getDb(): AppDatabase = AppDatabase.getDatabase(applicationContext)
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val actionExecutor by lazy { NotificationActionExecutor(PreferencesRepository(applicationContext)) }

    companion object {
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
            AppDatabase.getDatabase(context).notificationDao()
                .markAsRead(key, preferences.autoDeleteOnRead.value)
        }

        suspend fun markNotificationsAsRead(context: Context, keys: List<String>) {
            val preferences = PreferencesRepository(context)
            AppDatabase.getDatabase(context).notificationDao()
                .markAsReadBatch(keys, preferences.autoDeleteOnRead.value)
        }

        suspend fun markAllNotificationsAsRead(context: Context) {
            val preferences = PreferencesRepository(context)
            AppDatabase.getDatabase(context).notificationDao()
                .markAllAsRead(preferences.autoDeleteOnRead.value)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Icon capture is best-effort; any
    // failure (decode, IO, security) returns null and the caller falls back to no icon.
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
                Log.d(TAG, "onListenerConnected: processing ${activeNotifs.size} active notifications in batch")
                val entities = activeNotifs.mapNotNull { extractNotificationEntity(it) }
                if (entities.isNotEmpty()) {
                    val dao = getDb().notificationDao()
                    val ids = dao.insertNotifications(entities)
                    // F-G fix [2026-09-02 audit]: populate embeddings inline so the
                    // HybridSearchEngine (read-path) can rank new notifications
                    // semantically. Previously only DatabaseMigrator (one-shot v18
                    // backfill) wrote embeddings, leaving every post-migration
                    // notification unrankable. Pure-Kotlin compute, sub-ms per
                    // embedding, LRU-cached (256 entries), safe on the IO scope.
                    indexEmbeddingsForBatch(dao, ids, entities)
                    Log.d(TAG, "onListenerConnected: successfully batch inserted ${entities.size} active notifications")
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
        if (!BuildConfig.DEBUG && !isNotificationCaptureEnabled()) {
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
            actionExecutor.executeOnNotification(applicationContext, entity.key, entity.packageName, entity.title)
            scope.launch {
                try {
                    val dao = getDb().notificationDao()
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
                    indexEmbeddingForSingle(dao, finalEntity)
                    Log.d(TAG, "Inserted: ${finalEntity.title} (${finalEntity.appName})")
                } catch (e: Exception) {
                    Log.e(TAG, "DB insert failed for ${entity.title}", e)
                }
            }
        }
    }

    private fun registerNotificationActions(sbn: StatusBarNotification, key: String) {
        sbn.notification?.actions?.forEachIndexed { index, action ->
            NotificationLauncher.registerActionIntent(key, index, action.actionIntent)
        }
    }

    private fun isNotificationCaptureEnabled(): Boolean =
        com.jeffers.notimindlite.data.local.PreferencesRepository(applicationContext)
            .captureNotifications.value

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
        "TooGenericExceptionCaught", "SwallowedException", "ReturnCount", "LongMethod", "CyclomaticComplexMethod"
    ) // PackageManager lookups (app label, launch intent) intentionally return null on any failure so
    // extraction stays resilient across OEM variants; this method has 10 returns because each guard
    // returns early (blank content, summary, stale, missing app, etc.) — the function is intentionally
    // structured as a filter pipeline. Splitting it is tracked as future work; current contract is
    // well-covered by NotificationLoggerServiceTest.
    private fun shouldCaptureNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == applicationContext.packageName) return false
        val preferences = com.jeffers.notimindlite.data.local.PreferencesRepository(applicationContext)
        if (!preferences.captureOngoing.value && sbn.isOngoing) return false
        val notification = sbn.notification ?: return false
        if (preferences.captureActionsOnly.value && notification.actions.isNullOrEmpty()) return false
        val allow = parsePackageList(preferences.capturePackageAllowlist.value)
        val block = parsePackageList(preferences.capturePackageBlocklist.value)
        if (allow.isNotEmpty() && sbn.packageName !in allow) return false
        if (sbn.packageName in block) return false
        val importance = notification.channelId?.let {
            getSystemService(android.app.NotificationManager::class.java)
                ?.getNotificationChannel(it)
                ?.importance
        } ?: android.app.NotificationManager.IMPORTANCE_DEFAULT
        return importance >= preferences.minImportance.value
    }

    private fun parsePackageList(raw: String): Set<String> {
        return raw.split(',', '\n', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun extractNotificationEntity(sbn: StatusBarNotification): NotificationEntity? {
        if (!shouldCaptureNotification(sbn)) return null
        if (sbn.packageName == applicationContext.packageName) return null
        val notification = sbn.notification ?: return null
        val packageName = sbn.packageName

        return try {
            // Parse and sanitize fields in small focused helpers to reduce cyclomatic
            // complexity and make individual behaviors testable.
            val extras = notification.extras
            val (title, content, subText, bigText) = parseTitleAndContent(extras)
            val inboxLinesJson = extractInboxLinesJson(extras)
            val category = notification.category
            @Suppress("DEPRECATION")
            val priority = notification.priority
            val postTime = sbn.postTime
            val now = System.currentTimeMillis()

            val pipeline = com.jeffers.notimindlite.sanitization.SanitizationPipeline(applicationContext)
            val san = pipeline.sanitize(packageName, title, content, subText, bigText) ?: return null

            val finalTitle = if (PreferencesRepository(applicationContext).anonymizeTitles.value) "[REDACTED-TITLE]" else san.title
            val finalContent = san.content
            val finalSubText = san.subText
            val finalBigText = san.bigText

            if (finalTitle.isBlank() && finalContent.isBlank()) return null
            val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
            if (summaryRegex.matches(finalTitle) || summaryRegex.matches(finalContent) || (finalSubText != null && summaryRegex.matches(finalSubText))) return null

            val maxAgeMs = 30L * 24 * 60 * 60 * 1000L
            if (postTime > 0 && (now - postTime) > maxAgeMs) return null

            return buildNotificationEntity(
                NotificationBuildInput(sbn, notification, san, inboxLinesJson, postTime, now)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract notification entity", e)
            null
        }
    }

    private fun buildNotificationEntity(input: NotificationBuildInput): NotificationEntity {
        val sbn = input.sbn
        val notification = input.notification
        val san = input.san
        val packageName = sbn.packageName
        val isOngoing = sbn.isOngoing
        return NotificationEntity(
            key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}",
            packageName = packageName,
            appName = resolveAppName(packageName),
            appIconUri = getOrSaveAppIconUri(packageName),
            title = san.title,
            content = san.content,
            postTime = input.postTime,
            lastUpdatedTime = input.now,
            updateCount = 1,
            isDismissed = false,
            isPersistent = isOngoing,
            isRead = false,
            isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            category = notification.category,
            channelId = notification.channelId,
            subText = san.subText,
            bigText = san.bigText,
            inboxLinesJson = input.inboxLinesJson,
            priority = notification.priority,
            groupKey = sbn.groupKey,
            isOngoing = isOngoing,
            isClearable = sbn.isClearable,
            actionsCount = notification.actions?.size ?: 0,
            intentUri = safeLaunchIntentUri(packageName),
            isPinned = false,
            actionLabels = buildActionLabelsJson(notification.actions),
            smallIconRes = safeSmallIconRes(notification)
        )
    }

    private data class NotificationBuildInput(
        val sbn: StatusBarNotification,
        val notification: Notification,
        val san: com.jeffers.notimindlite.sanitization.SanitizationResult,
        val inboxLinesJson: String?,
        val postTime: Long,
        val now: Long
    )

    private fun parseTitleAndContent(extras: Bundle?): Quad<String, String, String?, String?> {
        val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val title = if (rawTitle.isNotBlank()) rawTitle else (conversationTitle ?: "")
        val rawContent = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val content = when {
            rawContent.isNotBlank() -> rawContent
            !bigText.isNullOrBlank() -> bigText
            !summaryText.isNullOrBlank() -> summaryText
            else -> ""
        }
        return Quad(title, content, subText, bigText)
    }

    private fun extractInboxLinesJson(extras: Bundle?): String? {
        return try {
            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) JSONArray(textLines.map { it.toString() }).toString() else null
        } catch (e: ClassCastException) {
            val stringLines = extras?.getStringArray(Notification.EXTRA_TEXT_LINES)
            if (!stringLines.isNullOrEmpty()) JSONArray(stringLines.toList()).toString() else null
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun safeSmallIconRes(notification: Notification): Int {
        return try {
            notification.smallIcon?.resId ?: 0
        } catch (e: IllegalStateException) {
            0
        }
    }

    private fun buildActionLabelsJson(actions: Array<android.app.Notification.Action>?): String? {
        if (actions.isNullOrEmpty()) return null
        val labels = mutableListOf<String>()
        actions.forEachIndexed { index, action ->
            labels.add(action.title?.toString() ?: "Action $index")
        }
        return JSONArray(labels).toString()
    }

    private fun safeLaunchIntentUri(packageName: String): String? {
        return try {
            packageManager.getLaunchIntentForPackage(packageName)?.toUri(Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            null
        }
    }

    // Small tuple-like holder to return four values without creating a top-level data class.
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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
        val dismissTime = System.currentTimeMillis()
        val effectiveReason = reason ?: 1

        scope.launch {
            try {
                val dao = getDb().notificationDao()
                dao.markDismissedWithReasonByMatching(
                    key = key,
                    packageName = sbn.packageName,
                    title = title,
                    content = content,
                    reason = effectiveReason,
                    dismissTime = dismissTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark notification dismissed for $key", e)
            }
        }
    }

    /**
     * F-G fix [2026-09-02 audit]: helper to populate the `embedding` BLOB column on a
     * single inserted row. Background context — pre-F-G only DatabaseMigrator wrote
     * embeddings (one-shot v18 backfill), so every post-migration notification had a
     * NULL embedding and was invisible to the semantic leg of HybridSearchEngine.
     */
    private suspend fun indexEmbeddingForSingle(dao: NotificationDao, entity: NotificationEntity) {
        try {
            val text = buildEmbeddingText(entity)
            val embedding = VectorEmbeddingHelper.computeEmbedding(text)
            // Room can't bind a FloatArray directly as a query parameter; it
            // expands each float into its own ? placeholder. Convert via the
            // same TypeConverter that the entity's `embedding` column uses,
            // so writes and reads round-trip through the same byte order.
            dao.updateEmbedding(entity.id, Converters().fromFloatArray(embedding) ?: return)
        } catch (e: Exception) {
            Log.w(TAG, "Embedding compute skipped for ${entity.key}: ${e.message}")
        }
    }

    /**
     * F-G fix [2026-09-02 audit]: batch variant for the onListenerConnected rehydrate.
     * Calls dao.updateEmbedding sequentially; could be replaced with
     * updateEmbeddingsBatch for large batches, but per the AGENTS.md "Additive
     * Preference" principle the safer choice is to reuse the existing sequential path
     * that DatabaseMigrator proves out at v18.
     */
    private suspend fun indexEmbeddingsForBatch(
        dao: NotificationDao,
        ids: List<Long>,
        entities: List<NotificationEntity>
    ) {
        if (ids.size != entities.size) {
            Log.w(TAG, "Batch embedding skipped: id/entity size mismatch (${ids.size} vs ${entities.size})")
            return
        }
        for (i in entities.indices) {
            indexEmbeddingForSingle(dao, entities[i].copy(id = ids[i]))
        }
    }

    /**
     * F-G fix [2026-09-02 audit]: match the text-shape DatabaseMigrator uses so the
     * on-capture embeddings live in the same feature space as the backfilled ones.
     */
    private fun buildEmbeddingText(entity: NotificationEntity): String =
        buildString {
            append(entity.appName).append(' ')
            append(entity.title).append(' ')
            append(entity.content).append(' ')
            if (!entity.subText.isNullOrEmpty()) append(entity.subText).append(' ')
            if (!entity.bigText.isNullOrEmpty()) append(entity.bigText).append(' ')
            if (!entity.category.isNullOrEmpty()) append(entity.category).append(' ')
            append(entity.packageName)
        }
}
