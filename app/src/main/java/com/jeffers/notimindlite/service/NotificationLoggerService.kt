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
        @Suppress("UnusedPrivateProperty") // Reserved for future debounce/filter tuning per F-A audit.
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
                    
                    val updateCount = if (hasSignificantChange) 1 else (existing?.updateCount ?: 0) + 1
                    val originalPostTime = if (hasSignificantChange) entity.postTime else (existing?.postTime ?: entity.postTime)
                    
                    val finalEntity = entity.copy(
                        id = if (hasSignificantChange) 0L else (existing?.id ?: 0L),
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
        val allow = preferences.capturePackageAllowlist.value.split(',', '\n', ' ', '\t')
            .map(String::trim).filter(String::isNotEmpty).toSet()
        val block = preferences.capturePackageBlocklist.value.split(',', '\n', ' ', '\t')
            .map(String::trim).filter(String::isNotEmpty).toSet()
        if (allow.isNotEmpty() && sbn.packageName !in allow) return false
        if (sbn.packageName in block) return false
        val importance = if (android.os.Build.VERSION.SDK_INT >= 26) {
            notification.channelId?.let { getSystemService(android.app.NotificationManager::class.java)?.getNotificationChannel(it)?.importance }
                ?: android.app.NotificationManager.IMPORTANCE_DEFAULT
        } else {
            (notification.priority + 2).coerceIn(0, 5)
        }
        return importance >= preferences.minImportance.value
    }

    private fun extractNotificationEntity(sbn: StatusBarNotification): NotificationEntity? {
        if (!shouldCaptureNotification(sbn)) return null
        try {
            if (sbn.packageName == applicationContext.packageName) return null
            val notification = sbn.notification ?: return null
            val packageName = sbn.packageName
            val extras = notification.extras
            val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            var title = if (rawTitle.isNotBlank()) rawTitle else (conversationTitle ?: "")

            val rawContent = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            var subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            var bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            var content = when {
                rawContent.isNotBlank() -> rawContent
                !bigText.isNullOrBlank() -> bigText
                !summaryText.isNullOrBlank() -> summaryText
                else -> ""
            }

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

            // Run sanitization pipeline (package filter + PII redaction). The pipeline returns null
            // to indicate the notification should be dropped (fail-closed).
            val pipeline = com.jeffers.notimindlite.sanitization.SanitizationPipeline(applicationContext)
            val san = pipeline.sanitize(packageName, title, content, subText, bigText) ?: return null
            // overwrite working variables with sanitized values
            title = san.title
            content = san.content
            subText = san.subText
            bigText = san.bigText
            if (PreferencesRepository(applicationContext).anonymizeTitles.value) {
                title = "[REDACTED-TITLE]"
            }

            if (title.isBlank() && content.isBlank()) return null

            val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
            if (summaryRegex.matches(title) ||
                summaryRegex.matches(content) ||
                (subText != null && summaryRegex.matches(subText))
            ) return null

            val maxAgeMs = 30L * 24 * 60 * 60 * 1000L
            if (postTime > 0 && (now - postTime) > maxAgeMs) return null

            // Always use a deterministic app-derived key. The system-provided sbn.key is
            // inconsistent between post/remove events on some devices, causing dismissal
            // lookups to miss the originally logged row.
            val key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
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

            return NotificationEntity(
                key = key,
                packageName = packageName,
                appName = appName,
                appIconUri = appIconUri,
                title = title,
                content = content,
                postTime = postTime,
                lastUpdatedTime = now,
                updateCount = 1,
                isDismissed = false,
                isPersistent = isOngoing,
                isRead = false,
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
                isPinned = false,
                actionLabels = actionLabelsJson,
                smallIconRes = smallIconRes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract notification entity", e)
            return null
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
