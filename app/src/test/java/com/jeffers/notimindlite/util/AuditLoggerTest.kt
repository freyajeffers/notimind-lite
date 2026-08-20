package com.jeffers.notimindlite.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.BackupRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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
}
