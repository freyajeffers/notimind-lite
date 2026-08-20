package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.data.local.BackupRecord
import com.jeffers.notimindlite.data.local.EncryptedBackupManager
import com.jeffers.notimindlite.data.local.generateBackupKey
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GcmIntegrityBoundaryTest : BaseRobolectricTest() {

    private lateinit var secretKey: SecretKey
    private lateinit var tempDir: File

    @Before
    override fun setup() {
        super.setup()
        secretKey = generateBackupKey()
        tempDir = Files.createTempDirectory("gcm_test").toFile()
    }

    private fun writeEncryptedFile(source: File, dest: File, key: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(source.readBytes())
        dest.writeBytes(iv + ciphertext)
    }

    private suspend fun registerBackupRecord(file: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        database.backupDao().insertRecord(
            BackupRecord(
                actionType = "EXPORT",
                fileHash = hash,
                signature = "test_sig",
                fileName = file.name
            )
        )
    }

    @Test
    fun `restoreAuthorizedBackup - corrupted ciphertext fails integrity check`() = runBlocking {
        val sourceFile = File(tempDir, "backup.enc")
        val destFile = File(tempDir, "db.restored")
        
        val originalDb = File(tempDir, "db.orig").apply { writeText("Sensitive Notification Data") }
        writeEncryptedFile(originalDb, sourceFile, secretKey)
        registerBackupRecord(sourceFile)
        
        // Corrupt the ciphertext (byte at index 20)
        val bytes = sourceFile.readBytes()
        if (bytes.size > 20) {
            bytes[20] = bytes[20].inc() 
            sourceFile.writeBytes(bytes)
        }
        
        val result = EncryptedBackupManager.restoreAuthorizedBackup(context, sourceFile, destFile, secretKey)
        assertFalse("Decryption should fail when ciphertext is corrupted", result)
    }

    @Test
    fun `restoreAuthorizedBackup - corrupted IV fails integrity check`() = runBlocking {
        val sourceFile = File(tempDir, "backup_iv.enc")
        val destFile = File(tempDir, "db_iv.restored")
        
        val originalDb = File(tempDir, "db_iv_orig").apply { writeText("Sensitive Notification Data") }
        writeEncryptedFile(originalDb, sourceFile, secretKey)
        registerBackupRecord(sourceFile)
        
        // Corrupt the IV (first byte)
        val bytes = sourceFile.readBytes()
        if (bytes.isNotEmpty()) {
            bytes[0] = bytes[0].inc()
            sourceFile.writeBytes(bytes)
        }
        
        val result = EncryptedBackupManager.restoreAuthorizedBackup(context, sourceFile, destFile, secretKey)
        assertFalse("Decryption should fail when IV is corrupted", result)
    }

    @Test
    fun `restoreAuthorizedBackup - truncated file fails`() = runBlocking {
        val sourceFile = File(tempDir, "backup_trunc.enc")
        val destFile = File(tempDir, "db_trunc.restored")
        
        val originalDb = File(tempDir, "db_trunc_orig").apply { writeText("Sensitive Notification Data") }
        writeEncryptedFile(originalDb, sourceFile, secretKey)
        registerBackupRecord(sourceFile)
        
        val bytes = sourceFile.readBytes()
        val truncated = bytes.copyOf(bytes.size - 8) 
        sourceFile.writeBytes(truncated)
        
        val result = EncryptedBackupManager.restoreAuthorizedBackup(context, sourceFile, destFile, secretKey)
        assertFalse("Decryption should fail when file is truncated", result)
    }

    @Test
    fun `restoreAuthorizedBackup - wrong key fails integrity check`() = runBlocking {
        val sourceFile = File(tempDir, "backup_key.enc")
        val destFile = File(tempDir, "db_key.restored")
        val wrongKey = generateBackupKey()
        
        val originalDb = File(tempDir, "db_key_orig").apply { writeText("Sensitive Notification Data") }
        writeEncryptedFile(originalDb, sourceFile, secretKey)
        registerBackupRecord(sourceFile)
        
        val result = EncryptedBackupManager.restoreAuthorizedBackup(context, sourceFile, destFile, wrongKey)
        assertFalse("Decryption should fail when the wrong key is used", result)
    }
}
