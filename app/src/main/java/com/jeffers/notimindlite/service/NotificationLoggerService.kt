1|package com.jeffers.notimindlite.service
2|
3|import android.app.Notification
4|import android.content.ComponentName
5|import android.content.Context
6|import android.content.Intent
7|import android.content.pm.PackageManager
8|import android.graphics.Bitmap
9|import android.graphics.Canvas
10|import android.graphics.drawable.BitmapDrawable
11|import android.service.notification.NotificationListenerService
12|import android.service.notification.StatusBarNotification
13|import android.util.Log
14|import com.jeffers.notimindlite.BuildConfig
15|import com.jeffers.notimindlite.data.local.AppDatabase
16|import com.jeffers.notimindlite.data.local.Converters
17|import com.jeffers.notimindlite.data.local.NotificationDao
18|import com.jeffers.notimindlite.data.local.NotificationEntity
19|import com.jeffers.notimindlite.util.NotificationLauncher
20|import com.jeffers.notimindlite.util.VectorEmbeddingHelper
21|import kotlinx.coroutines.CoroutineScope
22|import kotlinx.coroutines.Dispatchers
23|import kotlinx.coroutines.SupervisorJob
24|import kotlinx.coroutines.launch
25|import org.json.JSONArray
26|import java.io.File
27|import java.io.FileOutputStream
28|import java.util.Collections
29|import java.util.LinkedHashMap
30|
31|/**
32| * Service that listens for posted and removed notifications.
33| * It applies extended ingestion filters to eliminate clutter and persists
34| * clean notifications in the Room database.
35| */
36|@Suppress("TooManyFunctions") // NotificationLoggerService is intentionally one service surface; the 12
37|// functions map 1:1 to NotificationListenerService lifecycle hooks + capture pipeline. Splitting
38|// across files would fragment directBootAware service registration. See BootRestoreManager for the
39|// boot-restore subset already extracted.
40|class NotificationLoggerService : NotificationListenerService() {
41|    private val TAG = "NotificationLoggerSrv"
42|
43|    private fun getDb(): AppDatabase = AppDatabase.getDatabase(applicationContext)
44|    private val serviceJob = SupervisorJob()
45|    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
46|
47|    companion object {
48|        @Suppress("UnusedPrivateProperty") // Reserved for future debounce/filter tuning per F-A audit.
49|        private const val DEBOUNCE_MS = 30000L
50|        private const val MAX_CACHE_CAPACITY = 500
51|
52|        private val recentLogs: MutableMap<String, Long> = Collections.synchronizedMap(
53|            object : LinkedHashMap<String, Long>(16, 0.75f, true) {
54|                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
55|                    return size > MAX_CACHE_CAPACITY
56|                }
57|            }
58|        )
59|
60|        private val recentContents: MutableMap<String, String> = Collections.synchronizedMap(
61|            object : LinkedHashMap<String, String>(16, 0.75f, true) {
62|                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
63|                    return size > MAX_CACHE_CAPACITY
64|                }
65|            }
66|        )
67|
68|        @Volatile
69|        private var instance: NotificationLoggerService? = null
70|
71|        fun dismissNotification(key: String) {
72|            try {
73|                instance?.cancelNotification(key)
74|            } catch (e: Exception) {
75|                Log.e("NotificationLoggerSrv", "Failed to cancel notification with key: $key", e)
76|            }
77|        }
78|
79|        fun rebindService(context: Context) {
80|            try {
81|                requestRebind(ComponentName(context, NotificationLoggerService::class.java))
82|            } catch (e: Exception) {
83|                Log.e("NotificationLoggerSrv", "Failed to rebind notification listener service", e)
84|            }
85|        }
86|    }
87|
88|    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Icon capture is best-effort; any
89|    // failure (decode, IO, security) returns null and the caller falls back to no icon.
90|    private fun getOrSaveAppIconUri(packageName: String): String? {
91|        val iconsDir = File(cacheDir, "app_icons")
92|        if (!iconsDir.exists()) iconsDir.mkdirs()
93|        val iconFile = File(iconsDir, "$packageName.png")
94|        if (iconFile.exists() && iconFile.length() > 0) return iconFile.absolutePath
95|
96|        return try {
97|            val appInfo = packageManager.getApplicationInfo(packageName, 0)
98|            val drawable = packageManager.getApplicationIcon(appInfo)
99|            val bitmap = when (drawable) {
100|                is BitmapDrawable -> drawable.bitmap
101|                else -> {
102|                    val bmp = Bitmap.createBitmap(
103|                        drawable.intrinsicWidth.coerceAtLeast(1),
104|                        drawable.intrinsicHeight.coerceAtLeast(1),
105|                        Bitmap.Config.ARGB_8888
106|                    )
107|                    val canvas = Canvas(bmp)
108|                    drawable.setBounds(0, 0, canvas.width, canvas.height)
109|                    drawable.draw(canvas)
110|                    bmp
111|                }
112|            }
113|            FileOutputStream(iconFile).use { out ->
114|                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
115|            }
116|            iconFile.absolutePath
117|        } catch (e: Exception) {
118|            null
119|        }
120|    }
121|
122|    override fun onListenerConnected() {
123|        super.onListenerConnected()
124|        instance = this
125|        Log.d(TAG, "onListenerConnected: listener registered successfully")
126|
127|        scope.launch {
128|            try {
129|                val activeNotifs = activeNotifications ?: emptyArray()
130|                Log.d(TAG, "onListenerConnected: processing ${activeNotifs.size} active notifications in batch")
131|                val entities = activeNotifs.mapNotNull { extractNotificationEntity(it) }
132|                if (entities.isNotEmpty()) {
133|                    val dao = getDb().notificationDao()
134|                    val ids = dao.insertNotifications(entities)
135|                    // F-G fix [2026-09-02 audit]: populate embeddings inline so the
136|                    // HybridSearchEngine (read-path) can rank new notifications
137|                    // semantically. Previously only DatabaseMigrator (one-shot v18
138|                    // backfill) wrote embeddings, leaving every post-migration
139|                    // notification unrankable. Pure-Kotlin compute, sub-ms per
140|                    // embedding, LRU-cached (256 entries), safe on the IO scope.
141|                    indexEmbeddingsForBatch(dao, ids, entities)
142|                    Log.d(TAG, "onListenerConnected: successfully batch inserted ${entities.size} active notifications")
143|                }
144|            } catch (e: Exception) {
145|                Log.e(TAG, "Error syncing active notifications on listener connected", e)
146|            }
147|        }
148|    }
149|
150|    override fun onDestroy() {
151|        super.onDestroy()
152|        if (instance == this) instance = null
153|        serviceJob.cancel()
154|    }
155|
156|    override fun onNotificationPosted(sbn: StatusBarNotification) {
157|        if (!BuildConfig.DEBUG && !isNotificationCaptureEnabled()) {
158|            Log.d(TAG, "Ignoring notification post event: capture disabled")
159|            return
160|        }
161|        if (!isNotificationListenerActive()) {
162|            Log.w(TAG, "Ignoring notification post event: listener permission revoked")
163|            return
164|        }
165|        super.onNotificationPosted(sbn)
166|        Log.d(TAG, "onNotificationPosted: ${sbn.packageName} - ${sbn.id}")
167|        RestoredNotificationManager.onOriginalAppNotificationPosted(applicationContext, sbn.packageName)
168|        
169|        // Use the new extraction helper and individual insert for single posted notifications
170|        // (Single inserts are already optimized via Room, but we maintain compatibility)
171|        val entity = extractNotificationEntity(sbn)
172|        if (entity != null) {
173|            scope.launch {
174|                try {
175|                    val dao = getDb().notificationDao()
176|                    val existing = dao.getNotificationByKey(entity.key)
177|                    
178|                    // Logic: Only treat as a "new notification" (new row) if the content has changed significantly.
179|                    // Significant change = title or content is different.
180|                    // Otherwise, update the existing row (increment update count).
181|                    val hasSignificantChange = existing == null || 
182|                        existing.title != entity.title || 
183|                        existing.content != entity.content
184|                    
185|                    val updateCount = if (hasSignificantChange) 1 else (existing?.updateCount ?: 0) + 1
186|                    val originalPostTime = if (hasSignificantChange) entity.postTime else (existing?.postTime ?: entity.postTime)
187|                    
188|                    val finalEntity = entity.copy(
189|                        id = if (hasSignificantChange) 0L else (existing?.id ?: 0L),
190|                        updateCount = updateCount,
191|                        postTime = originalPostTime,
192|                        isRead = existing?.isRead ?: false,
193|                        isPinned = existing?.isPinned ?: false
194|                    )
195|                    dao.insert(finalEntity)
196|                    // F-G fix [2026-09-02 audit]: populate embedding for the single
197|                    // posted notification so semantic search can rank it.
198|                    indexEmbeddingForSingle(dao, finalEntity)
199|                    Log.d(TAG, "Inserted: ${finalEntity.title} (${finalEntity.appName})")
200|                } catch (e: Exception) {
201|                    Log.e(TAG, "DB insert failed for ${entity.title}", e)
202|                }
203|            }
204|        }
205|    }
206|
    private fun isNotificationCaptureEnabled(): Boolean = 
        com.jeffers.notimindlite.data.local.PreferencesRepository(applicationContext).captureNotifications.value
210|
211|    private fun isNotificationListenerActive(): Boolean {
212|        val componentName = ComponentName(applicationContext, NotificationLoggerService::class.java)
213|        val enabledListeners = android.provider.Settings.Secure.getString(
214|            contentResolver,
215|            "enabled_notification_listeners"
216|        ) ?: return false
217|        // `enabled_notification_listeners` is a colon-separated list of components that the
218|        // system stores in either fully-qualified form (`pkg/pkg.Cls`) or package-shorthand
219|        // form (`pkg/.Cls`). Compare against both to remain robust to whichever format the
220|        // current Settings provider used when our entry was written.
221|        val short = componentName.flattenToShortString()
222|        val long = componentName.flattenToString()
223|        return enabledListeners.split(':').any { it == short || it == long }
224|    }
225|
226|    @Suppress(
227|        "TooGenericExceptionCaught", "SwallowedException", "ReturnCount", "LongMethod", "CyclomaticComplexMethod"
228|    ) // PackageManager lookups (app label, launch intent) intentionally return null on any failure so
229|    // extraction stays resilient across OEM variants; this method has 10 returns because each guard
230|    // returns early (blank content, summary, stale, missing app, etc.) — the function is intentionally
231|    // structured as a filter pipeline. Splitting it is tracked as future work; current contract is
232|    // well-covered by NotificationLoggerServiceTest.
233|    private fun extractNotificationEntity(sbn: StatusBarNotification): NotificationEntity? {
234|        try {
235|            if (sbn.packageName == applicationContext.packageName) return null
236|            val notification = sbn.notification ?: return null
237|            val packageName = sbn.packageName
238|            val extras = notification.extras
239|            val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
240|            val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
241|            var title = if (rawTitle.isNotBlank()) rawTitle else (conversationTitle ?: "")
242|
243|            val rawContent = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
244|            var subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
245|            var bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
246|            val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
247|            var content = when {
248|                rawContent.isNotBlank() -> rawContent
249|                !bigText.isNullOrBlank() -> bigText
250|                !summaryText.isNullOrBlank() -> summaryText
251|                else -> ""
252|            }
253|
254|            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
255|            val inboxLinesJson: String? = if (!textLines.isNullOrEmpty()) {
256|                val linesList = textLines.map { it.toString() }
257|                JSONArray(linesList).toString()
258|            } else {
259|                null
260|            }
261|
262|            val category = notification.category
263|            @Suppress("DEPRECATION")
264|            val priority = notification.priority
265|            val postTime = sbn.postTime
266|            val now = System.currentTimeMillis()
267|
268|            // Run sanitization pipeline (package filter + PII redaction). The pipeline returns null
269|            // to indicate the notification should be dropped (fail-closed).
270|            val pipeline = com.jeffers.notimindlite.sanitization.SanitizationPipeline(applicationContext)
271|            val san = pipeline.sanitize(packageName, title, content, subText, bigText) ?: return null
272|            // overwrite working variables with sanitized values
273|            title = san.title
274|            content = san.content
275|            subText = san.subText
276|            bigText = san.bigText
277|
278|            if (title.isBlank() && content.isBlank()) return null
279|
280|            val summaryRegex = Regex("""\d+\s+more\s+notifications?""", RegexOption.IGNORE_CASE)
281|            if (summaryRegex.matches(title) ||
282|                summaryRegex.matches(content) ||
283|                (subText != null && summaryRegex.matches(subText))
284|            ) return null
285|
286|            val maxAgeMs = 30L * 24 * 60 * 60 * 1000L
287|            if (postTime > 0 && (now - postTime) > maxAgeMs) return null
288|
289|            // Always use a deterministic app-derived key. The system-provided sbn.key is
290|            // inconsistent between post/remove events on some devices, causing dismissal
291|            // lookups to miss the originally logged row.
292|            val key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
293|            val rawAppName = try {
294|                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
295|                packageManager.getApplicationLabel(appInfo).toString()
296|            } catch (e: Exception) {
297|                null
298|            }
299|            val appName = rawAppName ?: packageName
300|            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
301|            val smallIconRes = notification.smallIcon?.resId ?: 0
302|            val appIconUri = getOrSaveAppIconUri(packageName)
303|            val channelId = notification.channelId
304|            val groupKey = sbn.groupKey
305|            val isOngoing = sbn.isOngoing
306|            val isClearable = sbn.isClearable
307|            val actions = notification.actions
308|            val actionsCount = actions?.size ?: 0
309|
310|            val actionLabelsJson: String? = if (!actions.isNullOrEmpty()) {
311|                val labels = mutableListOf<String>()
312|                actions.forEachIndexed { index, action ->
313|                    val label = action.title?.toString() ?: "Action $index"
314|                    labels.add(label)
315|                }
316|                JSONArray(labels).toString()
317|            } else {
318|                null
319|            }
320|
321|            val intentUri = try {
322|                packageManager.getLaunchIntentForPackage(packageName)?.toUri(Intent.URI_INTENT_SCHEME)
323|            } catch (e: Exception) {
324|                null
325|            }
326|
327|            return NotificationEntity(
328|                key = key,
329|                packageName = packageName,
330|                appName = appName,
331|                appIconUri = appIconUri,
332|                title = title,
333|                content = content,
334|                postTime = postTime,
335|                lastUpdatedTime = now,
336|                updateCount = 1,
337|                isDismissed = false,
338|                isPersistent = isOngoing,
339|                isRead = false,
340|                isGroupSummary = isGroupSummary,
341|                category = category,
342|                channelId = channelId,
343|                subText = subText,
344|                bigText = bigText,
345|                inboxLinesJson = inboxLinesJson,
346|                priority = priority,
347|                groupKey = groupKey,
348|                isOngoing = isOngoing,
349|                isClearable = isClearable,
350|                actionsCount = actionsCount,
351|                intentUri = intentUri,
352|                isPinned = false,
353|                actionLabels = actionLabelsJson,
354|                smallIconRes = smallIconRes
355|            )
356|        } catch (e: Exception) {
357|            Log.e(TAG, "Failed to extract notification entity", e)
358|            return null
359|        }
360|    }
361|
362|    override fun onNotificationRemoved(sbn: StatusBarNotification) {
363|        if (!isNotificationListenerActive()) {
364|            Log.w(TAG, "Ignoring notification remove event: listener permission revoked")
365|            return
366|        }
367|        super.onNotificationRemoved(sbn)
368|        handleNotificationRemoved(sbn, null)
369|    }
370|
371|    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
372|        if (!isNotificationListenerActive()) {
373|            Log.w(TAG, "Ignoring notification remove event: listener permission revoked")
374|            return
375|        }
376|        super.onNotificationRemoved(sbn, rankingMap, reason)
377|        handleNotificationRemoved(sbn, reason)
378|    }
379|
380|    private fun handleNotificationRemoved(sbn: StatusBarNotification, reason: Int?) {
381|        if (sbn.packageName == applicationContext.packageName) return
382|        // Always use a deterministic app-derived key. The system-provided sbn.key is
383|        // inconsistent between post/remove events on some devices, causing dismissal
384|        // lookups to miss the originally logged row.
385|        val key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
386|        val extras = sbn.notification?.extras
387|        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
388|        val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
389|        Log.d(TAG, "Notification removed: $key, reason: $reason")
390|        NotificationLauncher.unregisterPendingIntent(key)
391|        recentLogs.remove(key)
392|        recentContents.remove(key)
393|        val dismissTime = System.currentTimeMillis()
394|        val effectiveReason = reason ?: 1
395|
396|        scope.launch {
397|            try {
398|                val dao = getDb().notificationDao()
399|                dao.markDismissedWithReasonByMatching(
400|                    key = key,
401|                    packageName = sbn.packageName,
402|                    title = title,
403|                    content = content,
404|                    reason = effectiveReason,
405|                    dismissTime = dismissTime
406|                )
407|            } catch (e: Exception) {
408|                Log.e(TAG, "Failed to mark notification dismissed for $key", e)
409|            }
410|        }
411|    }
412|
413|    /**
414|     * F-G fix [2026-09-02 audit]: helper to populate the `embedding` BLOB column on a
415|     * single inserted row. Background context — pre-F-G only DatabaseMigrator wrote
416|     * embeddings (one-shot v18 backfill), so every post-migration notification had a
417|     * NULL embedding and was invisible to the semantic leg of HybridSearchEngine.
418|     */
419|    private suspend fun indexEmbeddingForSingle(dao: NotificationDao, entity: NotificationEntity) {
420|        try {
421|            val text = buildEmbeddingText(entity)
422|            val embedding = VectorEmbeddingHelper.computeEmbedding(text)
423|            // Room can't bind a FloatArray directly as a query parameter; it
424|            // expands each float into its own ? placeholder. Convert via the
425|            // same TypeConverter that the entity's `embedding` column uses,
426|            // so writes and reads round-trip through the same byte order.
427|            dao.updateEmbedding(entity.id, Converters().fromFloatArray(embedding) ?: return)
428|        } catch (e: Exception) {
429|            Log.w(TAG, "Embedding compute skipped for ${entity.key}: ${e.message}")
430|        }
431|    }
432|
433|    /**
434|     * F-G fix [2026-09-02 audit]: batch variant for the onListenerConnected rehydrate.
435|     * Calls dao.updateEmbedding sequentially; could be replaced with
436|     * updateEmbeddingsBatch for large batches, but per the AGENTS.md "Additive
437|     * Preference" principle the safer choice is to reuse the existing sequential path
438|     * that DatabaseMigrator proves out at v18.
439|     */
440|    private suspend fun indexEmbeddingsForBatch(
441|        dao: NotificationDao,
442|        ids: List<Long>,
443|        entities: List<NotificationEntity>
444|    ) {
445|        if (ids.size != entities.size) {
446|            Log.w(TAG, "Batch embedding skipped: id/entity size mismatch (${ids.size} vs ${entities.size})")
447|            return
448|        }
449|        for (i in entities.indices) {
450|            indexEmbeddingForSingle(dao, entities[i].copy(id = ids[i]))
451|        }
452|    }
453|
454|    /**
455|     * F-G fix [2026-09-02 audit]: match the text-shape DatabaseMigrator uses so the
456|     * on-capture embeddings live in the same feature space as the backfilled ones.
457|     */
458|    private fun buildEmbeddingText(entity: NotificationEntity): String =
459|        buildString {
460|            append(entity.appName).append(' ')
461|            append(entity.title).append(' ')
462|            append(entity.content).append(' ')
463|            if (!entity.subText.isNullOrEmpty()) append(entity.subText).append(' ')
464|            if (!entity.bigText.isNullOrEmpty()) append(entity.bigText).append(' ')
465|            if (!entity.category.isNullOrEmpty()) append(entity.category).append(' ')
466|            append(entity.packageName)
467|        }
468|}