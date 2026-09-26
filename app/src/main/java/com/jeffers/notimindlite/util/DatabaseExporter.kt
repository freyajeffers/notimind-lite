package com.jeffers.notimindlite.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.domain.backup.EncryptedBackupManager
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.sanitization.PiiRedactionEngine
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object DatabaseExporter {

    private const val TAG = "DatabaseExporter"

    /**
     * Orchestrates a full encrypted backup of the local database.
     * When [passphrase] is supplied, the backup is wrapped so it can be restored on
     * other devices or after app reinstall.
     */
    @Suppress("ReturnCount")
    suspend fun performEncryptedBackup(
        context: Context,
        secretKey: SecretKey,
        passphrase: CharArray? = null,
    ): Result<File> {
        return try {
            val preferences = PreferencesRepository(context.applicationContext)
            if (preferences.requirePassphrase.value && (passphrase == null || passphrase.isEmpty())) {
                return Result.failure(IllegalArgumentException("A passphrase is required for encrypted exports"))
            }
            if (!NetworkUtils.isInternetAvailable(context)) {
                return Result.failure(IllegalStateException("Active internet connection is required to create a backup"))
            }

            val dbFile = context.getDatabasePath("notifications.db")
            if (!dbFile.exists()) return Result.failure(Exception("Database file not found"))

            val backupFile = File(context.cacheDir, "notimind_backup_${System.currentTimeMillis()}.enc")

            val success = EncryptedBackupManager.createAuthorizedBackup(
                context = context,
                sourceDbFile = dbFile,
                destinationFile = backupFile,
                secretKey = secretKey,
                passphrase = passphrase,
            )

            if (success) Result.success(backupFile)
            else Result.failure(Exception("Backup encryption or notary authorization failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Backup process failed", e)
            Result.failure(e)
        }
    }

    /**
     * Restores an encrypted backup file into the local database.
     * When [passphrase] is provided, attempts cross-device / post-uninstall unwrap.
     *
     * [secretKey] is REQUIRED (no default) because the previous default
     * (`generateBackupKey(context)`) silently overrode any caller-provided key with a
     * device-local AndroidKeyStore key. For a passphrase-wrapped backup from another
     * device, the KeyStore key can NEVER decrypt the payload — `resolveDekForRestore`
     * uses `passphrase` to unwrap the DEK only when the file header is passphrase-wrapped;
     * if the caller failed to pass an explicit key alongside the passphrase, restore
     * failed with a confusing GCM error instead of a clear "missing key" diagnostic.
     * Callers that restore on the originating device must pass `generateBackupKey(context)`
     * explicitly; callers restoring cross-device must pass any throwaway key alongside
     * the passphrase (the key value is irrelevant once the passphrase unwraps the DEK).
     */
    @Suppress("LongParameterList")
    suspend fun performRestore(
        context: Context,
        backupFile: File,
        secretKey: SecretKey,
        passphrase: CharArray? = null,
    ): Result<Unit> {
        return try {
            if (!backupFile.exists()) {
                return Result.failure(IllegalArgumentException("Backup file does not exist"))
            }
            val destDbFile = context.getDatabasePath("notifications.db")
            val success = EncryptedBackupManager.restoreAuthorizedBackup(
                context = context,
                sourceBackupFile = backupFile,
                destinationDbFile = destDbFile,
                secretKey = secretKey,
                passphrase = passphrase,
            )
            if (success) Result.success(Unit)
            else Result.failure(IllegalStateException("Backup restoration or authorization failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Restore process failed", e)
            Result.failure(e)
        }
    }

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
                put("postTimeFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(notif.postTime)))
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

    private fun applyPrivacy(notification: NotificationEntity, context: Context?): NotificationEntity {
        val preferences = context?.let { PreferencesRepository(it.applicationContext) } ?: return notification
        fun redact(value: String): String = PiiRedactionEngine.redact(value) ?: "[REDACTED]"
        val title = if (preferences.exportAnonymize.value || preferences.anonymizeTitles.value) "[REDACTED-TITLE]" else notification.title
        return notification.copy(
            title = if (preferences.redactPii.value) redact(title) else title,
            content = if (preferences.redactPii.value) redact(notification.content) else notification.content,
            subText = notification.subText?.let { if (preferences.redactPii.value) redact(it) else it },
            bigText = notification.bigText?.let { if (preferences.redactPii.value) redact(it) else it }
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

    fun shareExportFile(
        context: Context,
        notifications: List<NotificationEntity>,
        isJson: Boolean = true,
        passphrase: CharArray? = null,
        biometricAuthenticated: Boolean = false,
    ) {
        try {
            val preferences = PreferencesRepository(context.applicationContext)
            val requestedFormat = if (isJson) "json" else "csv"
            val allowedFormats = preferences.exportFormats.value.split(',').map(String::trim).toSet()
            check(requestedFormat in allowedFormats) { "Export format '$requestedFormat' is disabled in Settings" }
            check(!preferences.exportRequiresBiometric.value || biometricAuthenticated) {
                "Biometric authentication is required before exporting"
            }
            if (preferences.requirePassphrase.value && (passphrase == null || passphrase.isEmpty())) {
                throw IllegalArgumentException("A passphrase is required for exports")
            }
            cleanupExportFiles(context)

            val encrypted = preferences.encryptedExports.value
            val extension = if (encrypted) "enc" else if (isJson) "json" else "csv"
            val fileName = "notimind_export_${System.currentTimeMillis()}.$extension"

            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, fileName)
            file.setReadable(true, true)
            file.setWritable(true, true)
            if (encrypted) {
                val plain = if (isJson) exportToJsonString(notifications, context) else exportToCsvString(notifications, context)
                file.writeBytes(encryptExport(plain.toByteArray(Charsets.UTF_8), context, passphrase))
            } else if (isJson) {
                exportToJsonFile(file, notifications, context)
            } else {
                val fileContent = exportToCsvString(notifications, context)
                FileWriter(file).use { writer ->
                    writer.write(fileContent)
                }
            }

            val uri: Uri = getExportFileUri(context, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (encrypted) "application/octet-stream" else if (isJson) "application/json" else "text/csv"
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

    /** AES-GCM envelope; without a passphrase the AES key is backed by Android Keystore. */
    internal fun encryptExport(payload: ByteArray, context: Context, passphrase: CharArray?): ByteArray {
        val salt = if (passphrase != null) BackupKeyWrap.generateSalt() else ByteArray(0)
        val key = if (passphrase != null) {
            BackupKeyWrap.deriveKek(passphrase, salt)
        } else {
            SecretKeySpec(SqlCipherKeyManager.getOrCreatePassphrase(context, "exports"), "AES")
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        return byteArrayOf('N'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte(), salt.size.toByte()) +
            salt + cipher.iv + cipher.doFinal(payload)
    }
}
