package com.jeffers.notimindlite.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.sanitization.PiiRedactionEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseExportHelper {

    fun exportToJsonString(notifications: List<NotificationEntity>, context: Context? = null): String {
        val jsonArray = JSONArray()
        for (notif in notifications.map { applyPrivacy(it, context) }) {
            val jsonObject = JSONObject().apply {
                put("id", notif.id)
                put("key", notif.key)
                put("packageName", notif.packageName)
                put("appName", notif.appName)
                put("title", notif.title)
                put("content", notif.content)
                put("subText", notif.subText ?: "")
                put("bigText", notif.bigText ?: "")
                put("category", notif.category ?: "")
                put("channelId", notif.channelId ?: "")
                put("priority", notif.priority)
                put("postTime", notif.postTime)
                put(
                    "postTimeFormatted",
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(notif.postTime))
                )
                put("isDismissed", notif.isDismissed)
                put("dismissTime", notif.dismissTime ?: 0L)
                put("dismissReason", notif.dismissReason ?: -1)
                put("isOngoing", notif.isOngoing)
                put("isClearable", notif.isClearable)
                put("isPinned", notif.isPinned)
                put("actionsCount", notif.actionsCount)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(2)
    }

    fun exportToNdjsonString(notifications: List<NotificationEntity>, context: Context? = null): String {
        val sb = StringBuilder()
        for (notif in notifications.map { applyPrivacy(it, context) }) {
            val jsonObject = JSONObject().apply {
                put("id", notif.id)
                put("key", notif.key)
                put("packageName", notif.packageName)
                put("appName", notif.appName)
                put("title", notif.title)
                put("content", notif.content)
                put("subText", notif.subText ?: "")
                put("bigText", notif.bigText ?: "")
                put("category", notif.category ?: "")
                put("channelId", notif.channelId ?: "")
                put("priority", notif.priority)
                put("postTime", notif.postTime)
                put(
                    "postTimeFormatted",
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(notif.postTime))
                )
                put("isDismissed", notif.isDismissed)
                put("dismissTime", notif.dismissTime ?: 0L)
                put("dismissReason", notif.dismissReason ?: -1)
                put("isOngoing", notif.isOngoing)
                put("isClearable", notif.isClearable)
                put("isPinned", notif.isPinned)
                put("actionsCount", notif.actionsCount)
            }
            sb.append(jsonObject.toString())
            sb.append("\n")
        }
        return sb.toString()
    }

    fun exportToJsonFile(file: File, notifications: List<NotificationEntity>, context: Context? = null) {
        file.outputStream().use { os ->
            android.util.JsonWriter(java.io.OutputStreamWriter(os, "UTF-8")).use { writer ->
                writer.setIndent("  ")
                writer.beginArray()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (notif in notifications.map { applyPrivacy(it, context) }) {
                    writer.beginObject()
                    writer.name("id").value(notif.id)
                    writer.name("key").value(notif.key)
                    writer.name("packageName").value(notif.packageName)
                    writer.name("appName").value(notif.appName)
                    writer.name("title").value(notif.title)
                    writer.name("content").value(notif.content)
                    writer.name("subText").value(notif.subText ?: "")
                    writer.name("bigText").value(notif.bigText ?: "")
                    writer.name("category").value(notif.category ?: "")
                    writer.name("channelId").value(notif.channelId ?: "")
                    writer.name("priority").value(notif.priority)
                    writer.name("postTime").value(notif.postTime)
                    writer.name("postTimeFormatted").value(dateFormat.format(Date(notif.postTime)))
                    writer.name("isDismissed").value(notif.isDismissed)
                    writer.name("dismissTime").value(notif.dismissTime ?: 0L)
                    writer.name("dismissReason").value(notif.dismissReason ?: -1)
                    writer.name("isOngoing").value(notif.isOngoing)
                    writer.name("isClearable").value(notif.isClearable)
                    writer.name("isPinned").value(notif.isPinned)
                    writer.name("actionsCount").value(notif.actionsCount)
                    writer.endObject()
                }
                writer.endArray()
            }
        }
    }

    fun exportToCsvString(notifications: List<NotificationEntity>, context: Context? = null): String {
        val sb = StringBuilder()
        sb.append("ID,Package,AppName,Title,Content,PostTime,IsDismissed,DismissReason,IsPinned\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (n in notifications.map { applyPrivacy(it, context) }) {
            val escapedAppName = sanitizeCsvField(n.appName)
            val escapedTitle = sanitizeCsvField(n.title)
            val escapedContent = sanitizeCsvField(n.content)
            val postTimeStr = dateFormat.format(Date(n.postTime))
            val rowParts = listOf(
                n.id,
                n.packageName,
                escapedAppName,
                escapedTitle,
                escapedContent,
                postTimeStr,
                n.isDismissed.toString(),
                (n.dismissReason?.toString() ?: ""),
                n.isPinned.toString()
            )
            sb.appendLine(rowParts.joinToString(","))
        }
        return sb.toString()
    }

    fun sanitizeCsvField(value: String?): String {
        if (value == null) return "\"\""
        var sanitized = value.replace("\"", "\"\"")
        val trimmedSpace = sanitized.trimStart(' ')
        if (isSpreadsheetFormula(trimmedSpace)) {
            sanitized = "'$sanitized"
        }
        return "\"$sanitized\""
    }

    private fun isSpreadsheetFormula(value: String): Boolean {
        val first = value.firstOrNull() ?: return false
        return when (first) {
            '=', '+', '-', '@', '\t', '\r' -> true
            else -> false
        }
    }

    fun applyPrivacy(notification: NotificationEntity, context: Context?): NotificationEntity {
        val preferences = context?.let { PreferencesRepository(it.applicationContext) } ?: return notification
        fun redact(value: String): String = PiiRedactionEngine.redact(value) ?: "[REDACTED]"
        val title = if (preferences.exportManager.anonymize.value || preferences.anonymizeTitles.value) {
            "[REDACTED-TITLE]"
        } else {
            notification.title
        }
        return notification.copy(
            title = if (preferences.securityManager.redactPii.value) redact(title) else title,
            content = if (preferences.securityManager.redactPii.value) {
                redact(notification.content)
            } else {
                notification.content
            },
            subText = notification.subText?.let { if (preferences.securityManager.redactPii.value) redact(it) else it },
            bigText = notification.bigText?.let { if (preferences.securityManager.redactPii.value) redact(it) else it }
        )
    }

    fun getExportFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun cleanupExportFiles(context: Context, maxAgeMillis: Long = 3600_000L) {
        try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) return
            val now = System.currentTimeMillis()
            cacheDir.listFiles()
                ?.filter { now - it.lastModified() > maxAgeMillis }
                ?.forEach(File::delete)
        } catch (e: SecurityException) {
            android.util.Log.e("DatabaseExporter", "Failed to clean up temporary export files: permission denied", e)
        } catch (e: java.io.IOException) {
            android.util.Log.e("DatabaseExporter", "Failed to clean up temporary export files: IO error", e)
        }
    }
}
