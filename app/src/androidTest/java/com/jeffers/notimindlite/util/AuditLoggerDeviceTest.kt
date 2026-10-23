package com.jeffers.notimindlite.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.BackupRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuditLoggerDeviceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanLog() {
        val storageContext = context.createDeviceProtectedStorageContext()
        val logFile = File(File(storageContext.filesDir, "audit"), "notimind_persistent_audit.log")
        if (logFile.exists()) {
            logFile.delete()
        }
    }

    @Test
    fun auditLogger_deviceId_isConsistentAcrossCalls() {
        val deviceId1 = AuditLogger.getDeviceId(context)
        val deviceId2 = AuditLogger.getDeviceId(context)

        assertNotNull(deviceId1)
        assertTrue(deviceId1.isNotBlank())
        assertEquals(deviceId1, deviceId2)
    }

    @Test
    fun auditLogger_resolveUserId_returnsDevicePrefixedIdOnDevice() {
        val userId = AuditLogger.resolveUserId(context)
        assertNotNull(userId)
        assertTrue(userId.startsWith("device_"))
    }

    @Test
    fun auditLogger_appendAndRead_preservesIntegrityOnFlashStorage() {
        val signature = AuditLogger.appendPersistentLog(
            context,
            "DEVICE_HW_AUDIT",
            "Hardware test event on Android 16"
        )
        assertNotNull(signature)
        assertTrue(signature.isNotBlank())

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("DEVICE_HW_AUDIT", entry.eventType)
        assertEquals("Hardware test event on Android 16", entry.details)
        assertEquals(signature, entry.signature)
        assertTrue("Log entry signature must be valid on device", entry.isValid)
    }

    @Test
    fun auditLogger_tamperDetection_identifiesModifiedLogOnDisk() {
        AuditLogger.appendPersistentLog(context, "AUTHENTIC_RECORD", "unaltered_payload")

        val storageContext = context.createDeviceProtectedStorageContext()
        val logFile = File(File(storageContext.filesDir, "audit"), "notimind_persistent_audit.log")
        assertTrue(logFile.exists())

        // Tamper with the details field
        val content = logFile.readText(Charsets.UTF_8)
        val tampered = content.replace("unaltered_payload", "malicious_modification")
        logFile.writeText(tampered, Charsets.UTF_8)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertEquals(1, entries.size)
        val tamperedEntry = entries[0]
        assertEquals("AUTHENTIC_RECORD", tamperedEntry.eventType)
        assertEquals("malicious_modification", tamperedEntry.details)
        assertFalse("Tampered log on disk must fail cryptographic verification", tamperedEntry.isValid)
    }

    @Test
    fun auditLogger_logBackupEvent_recordsExportAndImportProperly() = runBlocking {
        val exportRecord = BackupRecord(
            actionType = "EXPORT",
            fileHash = "device_hash_export_12345",
            signature = "sig_export_device",
            fileName = "notimind_export_dev.enc",
            logMessage = "Device export test"
        )
        val importRecord = BackupRecord(
            actionType = "IMPORT",
            fileHash = "device_hash_import_67890",
            signature = "sig_import_device",
            fileName = "notimind_import_dev.enc",
            logMessage = "Device import test"
        )

        AuditLogger.logBackupEvent(context, exportRecord)
        AuditLogger.logBackupEvent(context, importRecord)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertEquals(2, entries.size)

        val exportEntry = entries.find { it.eventType == "BACKUP_EXPORT" }
        assertNotNull(exportEntry)
        assertTrue(exportEntry!!.isValid)
        assertTrue(exportEntry.details.contains("device_hash_export_12345"))

        val importEntry = entries.find { it.eventType == "BACKUP_IMPORT" }
        assertNotNull(importEntry)
        assertTrue(importEntry!!.isValid)
        assertTrue(importEntry.details.contains("device_hash_import_67890"))
    }
}
