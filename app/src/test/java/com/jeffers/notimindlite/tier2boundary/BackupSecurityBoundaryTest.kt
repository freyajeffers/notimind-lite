package com.jeffers.notimindlite.tier2boundary

import com.jeffers.notimindlite.base.BaseRobolectricTest
import com.jeffers.notimindlite.domain.backup.EncryptedBackupManager
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupSecurityBoundaryTest : BaseRobolectricTest() {
    private fun generateKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun createEncryptedBackupFile(sourceText: String, secretKey: SecretKey): File {
        val sourceDb = File.createTempFile("source", ".db").apply { writeText(sourceText) }
        val backupFile = File.createTempFile("backup", ".enc")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        FileInputStream(sourceDb).use { fis ->
            FileOutputStream(backupFile).use { fos ->
                fos.write(iv)
                encryptStream(fis, fos, cipher)
            }
        }
        sourceDb.delete()
        return backupFile
    }

    private fun encryptStream(input: FileInputStream, output: FileOutputStream, cipher: Cipher) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            cipher.update(buffer, 0, bytesRead)?.let(output::write)
        }
        cipher.doFinal()?.let(output::write)
    }

    @Test
    fun restoreAuthorizedBackup_should_fail_when_backup_file_is_missing() = runTest {
        val result = EncryptedBackupManager.restoreAuthorizedBackup(
            context, File("non_existent_backup.enc"), File("restored.db"), generateKey()
        )
        assertFalse("Restore should fail if file does not exist", result)
    }

    @Test
    fun restoreAuthorizedBackup_should_fail_when_hash_is_not_found_in_local_DB() = runTest {
        val backupFile = createEncryptedBackupFile("Sensitive Data", generateKey())
        val result = EncryptedBackupManager.restoreAuthorizedBackup(
            context, backupFile, File("restored.db"), generateKey()
        )
        assertFalse("Restore should fail if backup hash is not registered in local DB", result)
        backupFile.delete()
    }

    @Test
    fun restoreAuthorizedBackup_should_fail_when_ciphertext_is_corrupted() = runTest {
        val secretKey = generateKey()
        val backupFile = createEncryptedBackupFile("Sensitive Data", secretKey)
        val bytes = backupFile.readBytes()
        if (bytes.isNotEmpty()) {
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        }
        backupFile.writeBytes(bytes)

        val result = EncryptedBackupManager.restoreAuthorizedBackup(
            context, backupFile, File("restored.db"), secretKey
        )
        assertFalse("Restore should fail when GCM auth tag is corrupted", result)
        backupFile.delete()
    }

    @Test
    fun restoreAuthorizedBackup_should_fail_when_wrong_secret_key_is_used() = runTest {
        val backupFile = createEncryptedBackupFile("Sensitive Data", generateKey())
        val wrongKey = generateKey()
        val result = EncryptedBackupManager.restoreAuthorizedBackup(
            context, backupFile, File("restored.db"), wrongKey
        )
        assertFalse("Restore should fail when provided key does not match original encryption key", result)
        backupFile.delete()
    }
}
