package com.jeffers.notimindlite.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jeffers.notimindlite.data.local.BackupRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AuditLogger manages persistent on-disk signed audit logs and synchronizes
 * security events (such as app data clearance and backup operations) to Firestore.
 * 
 * If unauthenticated, the unique device identifier (ANDROID_ID) is used as userId.
 */
object AuditLogger {
    private const val TAG = "AuditLogger"
    private const val LOG_FILE_NAME = "notimind_persistent_audit.log"
    private const val HMAC_KEY_SALT = "NotiMind_TamperProof_Audit_Key_Salt_2026"

    /**
     * Resolves the effective user identifier for Firestore logging.
     * Uses FirebaseAuth UID if logged in, otherwise falls back to the device's ANDROID_ID.
     */
    fun resolveUserId(context: Context): String {
        val authUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }

        if (!authUid.isNullOrBlank()) {
            return authUid
        }

        val deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        return if (!deviceId.isNullOrBlank()) {
            "device_$deviceId"
        } else {
            "device_${Build.BOARD}_${Build.MODEL.hashCode()}"
        }
    }

    /**
     * Retrieves the unique device identifier.
     */
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${Build.MODEL}"
        } catch (e: Exception) {
            "unknown_device_${Build.MODEL}"
        }
    }

    /**
     * Generates a cryptographic HMAC-SHA256 signature for a log entry.
     */
    fun signLogEntry(data: String, deviceId: String): String {
        val secretKeyBytes = (HMAC_KEY_SALT + deviceId).toByteArray(StandardCharsets.UTF_8)
        val keySpec = SecretKeySpec(secretKeyBytes, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val hmacBytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }

    /**
     * Verifies an HMAC-SHA256 signature for a log entry.
     */
    fun verifySignature(data: String, signature: String, deviceId: String): Boolean {
        val expected = signLogEntry(data, deviceId)
        return expected == signature
    }

    /**
     * Gets the persistent storage directory for audit logs.
     * Uses Device Protected Storage (Direct Boot) if available so logs survive across credential encryption state.
     */
    private fun getPersistentLogFile(context: Context): File {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.createDeviceProtectedStorageContext()
            } catch (e: Exception) {
                context
            }
        } else {
            context
        }
        val dir = File(storageContext.filesDir, "audit")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE_NAME)
    }

    /**
     * Appends a signed entry to the persistent on-disk audit log.
     */
    @Synchronized
    fun appendPersistentLog(context: Context, eventType: String, details: String): String {
        val deviceId = getDeviceId(context)
        val timestamp = System.currentTimeMillis()
        val rawData = "$timestamp|$eventType|$deviceId|$details"
        val signature = signLogEntry(rawData, deviceId)
        val signedLine = "$rawData|$signature\n"

        try {
            val logFile = getPersistentLogFile(context)
            FileOutputStream(logFile, true).use { fos ->
                fos.write(signedLine.toByteArray(StandardCharsets.UTF_8))
            }
            Log.d(TAG, "Audit log appended: $eventType (signed)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log to disk", e)
        }
        return signature
    }

    /**
     * Checks if app data clearance occurred.
     * Uses persistent marker in Device Protected Storage vs Credential Encrypted Preferences.
     */
    suspend fun checkAndLogAppDataCleared(context: Context) = withContext(Dispatchers.IO) {
        try {
            val deviceContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    context.createDeviceProtectedStorageContext()
                } catch (e: Exception) {
                    context
                }
            } else {
                context
            }

            val devicePrefs = deviceContext.getSharedPreferences("notimind_device_audit_marker", Context.MODE_PRIVATE)
            val regularPrefs = context.getSharedPreferences("notimind_regular_state", Context.MODE_PRIVATE)

            val hasPriorInstallMarker = devicePrefs.getBoolean("initialized_prior", false)
            val hasRegularState = regularPrefs.getBoolean("app_state_valid", false)

            if (hasPriorInstallMarker && !hasRegularState) {
                // App data was cleared while device protected storage or prior installation marker persisted!
                Log.w(TAG, "ALERT: App data clearance detected!")
                val deviceId = getDeviceId(context)
                val userId = resolveUserId(context)
                val timestamp = System.currentTimeMillis()

                val signature = appendPersistentLog(context, "APP_DATA_CLEARED", "AppDataReset detected on device")

                // Push to Firestore
                uploadAuditLogToFirestore(
                    context = context,
                    eventType = "APP_DATA_CLEARED",
                    details = "Application data was cleared on device",
                    signature = signature,
                    timestamp = timestamp,
                    userId = userId,
                    deviceId = deviceId
                )
            }

            // Set markers for future detection
            devicePrefs.edit().putBoolean("initialized_prior", true).apply()
            regularPrefs.edit().putBoolean("app_state_valid", true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app data clear status", e)
        }
    }

    /**
     * Logs a backup event to both the persistent on-disk signed log and Firestore.
     */
    suspend fun logBackupEvent(
        context: Context,
        record: BackupRecord
    ) = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)
        val userId = resolveUserId(context)
        val details = "action=${record.actionType},hash=${record.fileHash},file=${record.fileName}"
        
        val signature = appendPersistentLog(context, "BACKUP_${record.actionType}", details)

        uploadAuditLogToFirestore(
            context = context,
            eventType = "BACKUP_${record.actionType}",
            details = details,
            signature = signature,
            timestamp = record.timestamp,
            userId = userId,
            deviceId = deviceId,
            extraData = mapOf(
                "fileHash" to record.fileHash,
                "fileName" to record.fileName,
                "remoteSignature" to (record.signature ?: ""),
                "logMessage" to record.logMessage
            )
        )
    }

    /**
     * Uploads an audit log entry to Firestore.
     */
    private fun uploadAuditLogToFirestore(
        context: Context,
        eventType: String,
        details: String,
        signature: String,
        timestamp: Long,
        userId: String,
        deviceId: String,
        extraData: Map<String, Any?> = emptyMap()
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val logData = mutableMapOf<String, Any>(
                "eventType" to eventType,
                "details" to details,
                "signature" to signature,
                "timestamp" to timestamp,
                "userId" to userId,
                "deviceId" to deviceId
            )
            extraData.forEach { (k, v) ->
                if (v != null) {
                    logData[k] = v
                }
            }

            val logId = "${timestamp}_${eventType}_${deviceId.take(8)}"
            
            // Save under user-scoped collection and global audit collection
            firestore.collection("users")
                .document(userId)
                .collection("audit_logs")
                .document(logId)
                .set(logData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Audit log synced to Firestore: $logId for user $userId")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to sync audit log to Firestore (will retry on next sync): ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore audit log upload skipped or failed: ${e.message}")
        }
    }

    /**
     * Reads all persistent log entries from disk and verifies their integrity.
     */
    fun readAndVerifyPersistentLogs(context: Context): List<PersistentAuditEntry> {
        val logFile = getPersistentLogFile(context)
        if (!logFile.exists()) return emptyList()

        val entries = mutableListOf<PersistentAuditEntry>()
        val deviceId = getDeviceId(context)

        try {
            logFile.forEachLine { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 5) {
                        val timestamp = parts[0].toLongOrNull() ?: 0L
                        val eventType = parts[1]
                        val entryDeviceId = parts[2]
                        val details = parts[3]
                        val signature = parts[4]
                        val rawData = "$timestamp|$eventType|$entryDeviceId|$details"
                        val isValid = verifySignature(rawData, signature, entryDeviceId)
                        entries.add(
                            PersistentAuditEntry(
                                timestamp = timestamp,
                                eventType = eventType,
                                deviceId = entryDeviceId,
                                details = details,
                                signature = signature,
                                isValid = isValid
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read persistent audit logs", e)
        }
        return entries
    }
}

data class PersistentAuditEntry(
    val timestamp: Long,
    val eventType: String,
    val deviceId: String,
    val details: String,
    val signature: String,
    val isValid: Boolean
)
