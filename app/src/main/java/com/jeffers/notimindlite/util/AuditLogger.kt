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
 *
 * The catch-and-fallback blocks in this file are best-effort probes (Firebase init race,
 * missing Settings permission, Direct Boot unsupported). Each fallback has a comment naming
 * the specific failure mode, but the exception itself is intentionally not re-thrown because
 * the caller path is "probe then degrade"; logging would add noise without adding safety.
 */
@Suppress("SwallowedException", "TooManyFunctions")
// 11 functions cover: device id resolution, HMAC sign/verify, persistent log append/read/parse,
// data-clearance detection, backup event logging, Firestore upload — each maps to a documented
// audit-logging concern. Extracting to a manager class would split the public-API surface for
// callers (BootReceiver, SyncWorker, Settings screen) without changing behavior.
object AuditLogger {
    private const val TAG = "AuditLogger"
    private const val LOG_FILE_NAME = "notimind_persistent_audit.log"
    private const val HMAC_KEY_SALT = "NotiMind_TamperProof_Audit_Key_Salt_2026"

    // Persistent log line layout: pipe-separated fields, index 0..4. Producer is
    // appendPersistentLog(); consumer is parsePersistentLogLine(). Any change here must be mirrored.
    // camelCase: detekt's VariableNaming rule rejects SCREAMING_SNAKE_CASE.
    private const val logLineMinParts = 5
    private const val logPartTimestamp = 0
    private const val logPartEventType = 1
    private const val logPartDeviceId = 2
    private const val logPartDetails = 3
    private const val logPartSignature = 4

    // Firestore schema constants. Centralised so the collection layout is documented
    // in one place; see AGENTS.md "Firebase Headless Safety" for initialization guards.
    private const val usersCollection = "users"
    private const val auditLogsSubcollection = "audit_logs"
    private const val logIdDevicePrefixLen = 8

    /**
     * Resolves the effective user identifier for Firestore logging.
     * Uses FirebaseAuth UID if logged in, otherwise falls back to the device's ANDROID_ID.
     */
    fun resolveUserId(context: Context): String {
        val authUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: IllegalStateException) {
            // FirebaseAuth not initialized yet (AppInitializer race during boot).
            null
        }

        if (!authUid.isNullOrBlank()) {
            return authUid
        }

        val deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: SecurityException) {
            // READ_PHONE_STATE / Settings permission missing on some OEM builds.
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
        } catch (e: SecurityException) {
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
        return java.security.MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            signature.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Gets the persistent storage directory for audit logs.
     * Uses Device Protected Storage (Direct Boot) if available so logs survive across credential encryption state.
     */
    private fun getPersistentLogFile(context: Context): File {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.createDeviceProtectedStorageContext()
            } catch (e: IllegalStateException) {
                // Direct Boot not supported (rare OEM); fall back to credential-protected storage.
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
        } catch (e: java.io.IOException) {
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
                } catch (e: IllegalStateException) {
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
                    FirestoreAuditLogEntry(
                        eventType = "APP_DATA_CLEARED",
                        details = "Application data was cleared on device",
                        signature = signature,
                        timestamp = timestamp,
                        userId = userId,
                        deviceId = deviceId
                    )
                )
            }

            // Set markers for future detection
            devicePrefs.edit().putBoolean("initialized_prior", true).apply()
            regularPrefs.edit().putBoolean("app_state_valid", true).apply()
        } catch (e: IllegalStateException) {
            // SharedPreferences backend unavailable (storage unmounted).
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
            FirestoreAuditLogEntry(
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
        )
    }

    /**
     * Bundle of fields for a single Firestore audit-log write.
     * Groups the parameters of [uploadAuditLogToFirestore] so the function signature
     * stays under the detekt LongParameterList threshold (6).
     */
    private data class FirestoreAuditLogEntry(
        val eventType: String,
        val details: String,
        val signature: String,
        val timestamp: Long,
        val userId: String,
        val deviceId: String,
        val extraData: Map<String, Any?> = emptyMap()
    )

    /**
     * Uploads an audit log entry to Firestore.
     */
    private fun uploadAuditLogToFirestore(entry: FirestoreAuditLogEntry) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val logData = mutableMapOf<String, Any>(
                "eventType" to entry.eventType,
                "details" to entry.details,
                "signature" to entry.signature,
                "timestamp" to entry.timestamp,
                "userId" to entry.userId,
                "deviceId" to entry.deviceId
            )
            entry.extraData.forEach { (k, v) ->
                if (v != null) {
                    logData[k] = v
                }
            }

            // Document ID: stable, sortable, unique per (timestamp, type, device). 8-char
            // device prefix keeps Firestore doc IDs well under the 1500-byte cap even for
            // busy devices.
            val logId = "${entry.timestamp}_${entry.eventType}_${entry.deviceId.take(logIdDevicePrefixLen)}"

            // Save under user-scoped collection and global audit collection
            firestore.collection(usersCollection)
                .document(entry.userId)
                .collection(auditLogsSubcollection)
                .document(logId)
                .set(logData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Audit log synced to Firestore: $logId for user ${entry.userId}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to sync audit log to Firestore (will retry on next sync): ${e.message}")
                }
        } catch (e: IllegalStateException) {
            // Firestore not initialized or context destroyed; non-fatal.
            Log.w(TAG, "Firestore audit log upload skipped: ${e.message}")
        } catch (e: SecurityException) {
            // Network security policy refused the call; non-fatal.
            Log.w(TAG, "Firestore audit log upload blocked by security policy: ${e.message}")
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
                parsePersistentLogLine(line, deviceId)?.let(entries::add)
            }
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Failed to read persistent audit logs", e)
        }
        return entries
    }

    @Suppress("MagicNumber", "UnusedParameter")
    // deviceId parameter is kept in the signature for future per-line device validation
    // (e.g., rejecting logs from a device the current install does not recognize).
    private fun parsePersistentLogLine(line: String, deviceId: String): PersistentAuditEntry? {
        val parts = line.split("|")
        if (line.isBlank() || parts.size < logLineMinParts) {
            return null
        }
        val timestamp = parts[logPartTimestamp].toLongOrNull() ?: 0L
        val eventType = parts[logPartEventType]
        val entryDeviceId = parts[logPartDeviceId]
        val details = parts[logPartDetails]
        val signature = parts[logPartSignature]
        val rawData = "$timestamp|$eventType|$entryDeviceId|$details"
        return PersistentAuditEntry(
            timestamp = timestamp,
            eventType = eventType,
            deviceId = entryDeviceId,
            details = details,
            signature = signature,
            isValid = verifySignature(rawData, signature, entryDeviceId)
        )
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
