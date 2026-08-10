package com.jeffers.notimindlite.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.jeffers.notimindlite.data.local.NotificationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseExporter {

    private const val TAG = "DatabaseExporter"

    fun exportToJsonString(notifications: List<NotificationEntity>): String {
        val jsonArray = JSONArray()
        for (notif in notifications) {
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
                put("postTimeFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(notif.postTime)))
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

    fun exportToCsvString(notifications: List<NotificationEntity>): String {
        val sb = StringBuilder()
        sb.append("ID,Package,AppName,Title,Content,PostTime,IsDismissed,DismissReason,IsPinned\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (n in notifications) {
            val escapedAppName = sanitizeCsvField(n.appName)
            val escapedTitle = sanitizeCsvField(n.title)
            val escapedContent = sanitizeCsvField(n.content)
            val postTimeStr = dateFormat.format(Date(n.postTime))
            sb.append("${n.id},${n.packageName},$escapedAppName,$escapedTitle,$escapedContent,$postTimeStr,${n.isDismissed},${n.dismissReason ?: ""},${n.isPinned}\n")
        }
        return sb.toString()
    }

    fun sanitizeCsvField(value: String?): String {
        if (value == null) return "\"\""
        var sanitized = value.replace("\"", "\"\"")
        val trimmedSpace = sanitized.trimStart(' ')
        if (trimmedSpace.startsWith("=") || trimmedSpace.startsWith("+") ||
            trimmedSpace.startsWith("-") || trimmedSpace.startsWith("@") ||
            trimmedSpace.startsWith("\t") || trimmedSpace.startsWith("\r")
        ) {
            sanitized = "'$sanitized"
        }
        return "\"$sanitized\""
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
            if (cacheDir.exists()) {
                val now = System.currentTimeMillis()
                cacheDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMillis) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up temporary export files", e)
        }
    }

    fun shareExportFile(context: Context, notifications: List<NotificationEntity>, isJson: Boolean = true) {
        try {
            cleanupExportFiles(context)

            val fileContent = if (isJson) exportToJsonString(notifications) else exportToCsvString(notifications)
            val extension = if (isJson) "json" else "csv"
            val fileName = "notimind_export_${System.currentTimeMillis()}.$extension"

            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(fileContent)
            }

            val uri: Uri = getExportFileUri(context, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (isJson) "application/json" else "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Export Notifications Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export database", e)
        }
    }
}
