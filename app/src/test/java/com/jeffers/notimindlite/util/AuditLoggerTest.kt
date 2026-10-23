package com.jeffers.notimindlite.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.BackupRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuditLoggerTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testResolveUserId_unauthenticated_usesDeviceIdFallback() {
        val userId = AuditLogger.resolveUserId(context)
        assertNotNull(userId)
        assertTrue(userId.startsWith("device_"))
    }

    @Test
    fun testSignAndVerifyLogEntry() {
        val deviceId = AuditLogger.getDeviceId(context)
        val rawData = "123456789|TEST_EVENT|$deviceId|details"

        val signature = AuditLogger.signLogEntry(rawData, deviceId)
        assertNotNull(signature)
        assertTrue(signature.isNotBlank())

        val isValid = AuditLogger.verifySignature(rawData, signature, deviceId)
        assertTrue("Signature must verify successfully", isValid)

        val isTamperedValid = AuditLogger.verifySignature("tampered_data", signature, deviceId)
        assertFalse("Tampered data must fail signature verification", isTamperedValid)
    }

    @Test
    fun testAppendAndReadPersistentLog() {
        val signature = AuditLogger.appendPersistentLog(context, "UNIT_TEST_EVENT", "test_details_payload")
        assertNotNull(signature)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertTrue("Entries must not be empty", entries.isNotEmpty())

        val testEntry = entries.find { it.eventType == "UNIT_TEST_EVENT" }
        assertNotNull("Must find appended unit test entry", testEntry)
        assertEquals("test_details_payload", testEntry!!.details)
        assertTrue("Entry must have valid signature", testEntry.isValid)
    }

    @Test
    fun testLogBackupEvent_writesSignedPersistentEntry() = runBlocking {
        val record = BackupRecord(
            actionType = "EXPORT",
            fileHash = "hash1234567890abcdef",
            signature = "remote_sig_xyz",
            fileName = "backup_test.enc",
            logMessage = "Unit test backup"
        )

        AuditLogger.logBackupEvent(context, record)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        val backupEntry = entries.find { it.eventType == "BACKUP_EXPORT" }
        assertNotNull("Backup export entry must exist", backupEntry)
        assertTrue("Backup entry must be cryptographically valid", backupEntry!!.isValid)
        assertTrue("Backup entry must contain file hash", backupEntry.details.contains("hash1234567890abcdef"))
    }

    @Test
    fun getDeviceId_isIdempotentAndPersistsAcrossCalls() {
        val id1 = AuditLogger.getDeviceId(context)
        val id2 = AuditLogger.getDeviceId(context)

        assertNotNull(id1)
        assertTrue(id1.isNotBlank())
        assertEquals(id1, id2)
    }

    @Test
    fun readAndVerifyPersistentLogs_filtersMalformedLines() {
        val storageContext = context.createDeviceProtectedStorageContext()
        val logFile = java.io.File(java.io.File(storageContext.filesDir, "audit"), "notimind_persistent_audit.log")
        logFile.parentFile?.mkdirs()

        // Append a valid entry first
        AuditLogger.appendPersistentLog(context, "VALID_EVENT", "valid_details")

        // Append malformed lines manually
        java.io.FileOutputStream(logFile, true).use { fos ->
            fos.write("\n".toByteArray(Charsets.UTF_8))
            fos.write("only|two|parts\n".toByteArray(Charsets.UTF_8))
            fos.write("part1|part2|part3|part4\n".toByteArray(Charsets.UTF_8))
        }

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertTrue(entries.any { it.eventType == "VALID_EVENT" })
        assertFalse(entries.any { it.eventType == "two" || it.eventType == "part2" })
    }

    @Test
    fun readAndVerifyPersistentLogs_detectsTamperingInPersistentFile() {
        val storageContext = context.createDeviceProtectedStorageContext()
        val logFile = java.io.File(java.io.File(storageContext.filesDir, "audit"), "notimind_persistent_audit.log")
        logFile.delete()

        AuditLogger.appendPersistentLog(context, "UNALTERED_EVENT", "legitimate_data")
        val content = logFile.readText(Charsets.UTF_8)
        val tamperedContent = content.replace("legitimate_data", "tampered_data")
        logFile.writeText(tamperedContent, Charsets.UTF_8)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        assertEquals(1, entries.size)
        val tamperedEntry = entries[0]
        assertEquals("UNALTERED_EVENT", tamperedEntry.eventType)
        assertEquals("tampered_data", tamperedEntry.details)
        assertFalse("Tampered log line must fail cryptographic signature verification", tamperedEntry.isValid)
    }

    @Test
    fun checkAndLogAppDataCleared_detectsClearanceWhenMarkerExistsAndAppStateMissing() = runBlocking {
        val deviceContext = context.createDeviceProtectedStorageContext()
        val devicePrefs = deviceContext.getSharedPreferences("notimind_device_audit_marker", Context.MODE_PRIVATE)
        val regularPrefs = context.getSharedPreferences("notimind_regular_state", Context.MODE_PRIVATE)

        // Simulate prior installation with cleared app state
        devicePrefs.edit().putBoolean("initialized_prior", true).commit()
        regularPrefs.edit().clear().commit()

        AuditLogger.checkAndLogAppDataCleared(context)

        val entries = AuditLogger.readAndVerifyPersistentLogs(context)
        val clearedEntry = entries.find { it.eventType == "APP_DATA_CLEARED" }
        assertNotNull("Must log APP_DATA_CLEARED event when data reset detected", clearedEntry)
        assertTrue(clearedEntry!!.isValid)

        // Verify markers are refreshed
        assertTrue(devicePrefs.getBoolean("initialized_prior", false))
        assertTrue(regularPrefs.getBoolean("app_state_valid", false))
    }
}
