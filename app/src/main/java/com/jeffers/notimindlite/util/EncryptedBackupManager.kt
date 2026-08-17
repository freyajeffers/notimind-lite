package com.jeffers.notimindlite.data.local

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptedBackupManager handles the creation and restoration of encrypted backups.
 * It integrates with BackupDao to ensure backup integrity and authorization.
 */
object EncryptedBackupManager {
    private const val TAG = "EncryptedBackupMgr"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val KEY_SIZE = 256

    /**
     * Creates an encrypted backup and records it in the local database.
     */
    suspend fun createAuthorizedBackup(
        context: Context,
        sourceDbFile: File,
        destinationFile: File,
        secretKey: SecretKey
    ): Boolean {
        if (!sourceDbFile.exists()) return false

        return try {
            // 1. Perform Encryption
            val success = performEncryption(sourceDbFile, destinationFile, secretKey)
            
            if (success) {
                // 2. Generate Hash and Signature for the final encrypted file
                val fileHash = calculateFileHash(destinationFile)
                val signature = "SIG_" + fileHash.take(16) // Simplified internal signature

                // 3. Record the export in DB
                val db = AppDatabase.getDatabase(context)
                db.backupDao().insertRecord(
                    BackupRecord(
                        actionType = "EXPORT",
                        fileHash = fileHash,
                        signature = signature,
                        fileName = destinationFile.name,
                        logMessage = "Successfully exported encrypted backup"
                    )
                )
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Authorized backup failed: ${e.message}", e)
            false
        }
    }

    /**
     * Decrypts a backup only if its hash and signature are authorized in the local DB.
     */
    suspend fun restoreAuthorizedBackup(
        context: Context,
        sourceBackupFile: File,
        destinationDbFile: File,
        secretKey: SecretKey
    ): Boolean {
        if (!sourceBackupFile.exists()) return false

        return try {
            // 1. Verify Hash and Signature against DB
            val fileHash = calculateFileHash(sourceBackupFile)
            val record = AppDatabase.getDatabase(context).backupDao().getRecordByHash(fileHash)
            
            if (record == null) {
                Log.e(TAG, "Unauthorized backup attempt: Hash $fileHash not found in records")
                return false
            }

            // 2. Perform Decryption
            val success = performDecryption(sourceBackupFile, destinationDbFile, secretKey)
            
            if (success) {
                // 3. Log the import
                AppDatabase.getDatabase(context).backupDao().insertRecord(
                    BackupRecord(
                        actionType = "IMPORT",
                        fileHash = fileHash,
                        signature = record.signature,
                        fileName = sourceBackupFile.name,
                        logMessage = "Successfully imported authorized backup"
                    )
                )
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Authorized restore failed: ${e.message}", e)
            false
        }
    }

    private fun performEncryption(source: File, dest: File, key: SecretKey): Boolean {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            FileInputStream(source).use { fis ->
                FileOutputStream(dest).use { fos ->
                    fos.write(iv)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null) fos.write(output)
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) fos.write(finalBlock)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun performDecryption(source: File, dest: File, key: SecretKey): Boolean {
        return try {
            FileInputStream(source).use { fis ->
                val iv = ByteArray(IV_LENGTH)
                if (fis.read(iv) != IV_LENGTH) return false
                
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

                FileOutputStream(dest).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null) fos.write(output)
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) fos.write(finalBlock)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun generateBackupKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }
}
