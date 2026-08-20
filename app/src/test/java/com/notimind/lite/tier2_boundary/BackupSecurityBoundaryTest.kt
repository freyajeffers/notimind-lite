package com.notimind.lite.tier2_boundary

import android.content.Context
import com.jeffers.notimindlite.data.local.EncryptedBackupManager
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class BackupSecurityBoundaryTest : BaseRobolectricTest() {

    private lateinit var secretKey: SecretKey

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        secretKey = keyGen.generateKey()
    }

    @Test
    fun `restoreAuthorizedBackup should fail when backup file is missing`() = runBlocking {
        val missingFile = File("non_existent_backup.enc")
        val destFile = File("restored.db")
        
        val result = EncryptedBackupManager.restoreAuthorizedBackup(applicationContext, missingFile, destFile, secretKey)
        
        assertFalse("Restore should fail if file does not exist", result)
    }

    @Test
    fun `restoreAuthorizedBackup should fail when hash is not found in local DB`() = runBlocking {
        val backupFile = File.createTempFile("backup", ".enc").apply { writeBytes(ByteArray(100)) }
        val destFile = File("restored.db")
        
        // In Robolectric, we use the real AppDatabase (in-memory via BaseRobolectricTest)
        // Ensure DB is empty of this hash
        val result = EncryptedBackupManager.restoreAuthorizedBackup(applicationContext, backupFile, destFile, secretKey)
        
        assertFalse("Restore should fail if backup hash is not registered in local DB", result)
        backupFile.delete()
    }

    @Test
    fun `restoreAuthorizedBackup should fail when ciphertext is corrupted`() = runBlocking {
        val sourceDb = File.createTempFile("source", ".db").apply { writeText("Sensitive Data") }
        val backupFile = File.createTempFile("backup", ".enc")
        
        // 1. Create a valid authorized backup
        EncryptedBackupManager.createAuthorizedBackup(applicationContext, sourceDb, backupFile, secretKey)
        
        // 2. Corrupt the ciphertext (flip a bit in the encrypted payload)
        val bytes = backupFile.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        backupFile.writeBytes(bytes)
        
        val destFile = File("restored.db")
        val result = EncryptedBackupManager.restoreAuthorizedBackup(applicationContext, backupFile, destFile, secretKey)
        
        assertFalse("Restore should fail when GCM auth tag is corrupted", result)
        
        sourceDb.delete()
        backupFile.delete()
    }

    @Test
    fun `restoreAuthorizedBackup should fail when wrong secret key is used`() = runBlocking {
        val sourceDb = File.createTempFile("source", ".db").apply { writeText("Sensitive Data") }
        val backupFile = File.createTempFile("backup", ".enc")
        
        // 1. Create backup with correct key
        EncryptedBackupManager.createAuthorizedBackup(applicationContext, sourceDb, backupFile, secretKey)
        
        // 2. Attempt restore with a different key
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val destFile = File("restored.db")
        val result = EncryptedBackupManager.restoreAuthorizedBackup(applicationContext, backupFile, destFile, wrongKey)
        
        assertFalse("Restore should fail when provided key does not match original encryption key", result)
        
        sourceDb.delete()
        backupFile.delete()
    }
}
