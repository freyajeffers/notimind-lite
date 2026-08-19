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
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Service that listens for posted and removed notifications.
 * It extracts a minimal set of fields and persists them in the Room database.
 */
class NotificationLoggerService : NotificationListenerService() {
    private val TAG = "NotificationLoggerSrv"

    private fun getDb(): AppDatabase = AppDatabase.getDatabase(applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        
        scope.launch {
            try {
                val activeNotifs = activeNotifications ?: emptyArray()
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
        scope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
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

            val category = notification.category
            val priority = notification.priority
            val postTime = sbn.postTime
            val now = System.currentTimeMillis()

            val key = sbn.key ?: "${sbn.id}|${sbn.packageName}|${sbn.postTime}"
            
            // Debounce logic
            val contentSignature = "$title|$content"
            val lastLogTime = recentLogs[key] ?: 0L
            val lastContent = recentContents[key]
            if (lastContent == contentSignature && (now - lastLogTime < DEBOUNCE_MS)) {
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

            scope.launch {
                val dao = getDb().notificationDao()
                val entity = NotificationEntity(
                    key = key,
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    content = content,
                    postTime = postTime,
                    category = category,
                    priority = priority
                )
                dao.insert(entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val key = sbn.key ?: "${sbn.id}|${sbn.packageName}|${sbn.postTime}"
        val dismissTime = System.currentTimeMillis()
        scope.launch {
            // In this basic version, we can't easily map SBN key to DB ID 
            // without a key-based lookup, so we simulate the call.
            // Real implementation in later commits will use a proper key-based DAO method.
            
        }
    }
}
