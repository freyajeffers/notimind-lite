package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptedBackupManager handles the creation of encrypted database backups.
 * It uses AES-GCM for authenticated encryption of the SQLite database file.
 */
object EncryptedBackupManager {
    private const val TAG = "EncryptedBackupMgr"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val KEY_SIZE = 256

    /**
     * Creates an encrypted backup of the specified database file.
     * 
     * @param context Application context.
     * @param sourceDbFile The SQLite database file to back up.
     * @param destinationFile The file where the encrypted backup will be saved.
     * @param secretKey The key used for encryption.
     * @return True if backup was successful, false otherwise.
     */
    fun createEncryptedBackup(
        context: Context,
        sourceDbFile: File,
        destinationFile: File,
        secretKey: SecretKey
    ): Boolean {
        if (!sourceDbFile.exists()) {
            Log.e(TAG, "Source database file does not exist: ${sourceDbFile.absolutePath}")
            return false
        }

        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            FileInputStream(sourceDbFile).use { fis ->
                FileOutputStream(destinationFile).use { fos ->
                    // Write IV to the start of the file
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
            Log.d(TAG, "Encrypted backup created successfully at: ${destinationFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted backup: ${e.message}", e)
            false
        }
    }

    /**
     * Decrypts an encrypted backup back into a usable SQLite database file.
     */
    fun decryptBackup(
        context: Context,
        sourceBackupFile: File,
        destinationDbFile: File,
        secretKey: SecretKey
    ): Boolean {
        if (!sourceBackupFile.exists()) return false

        return try {
            FileInputStream(sourceBackupFile).use { fis ->
                val iv = ByteArray(IV_LENGTH)
                if (fis.read(iv) != IV_LENGTH) throw Exception("Invalid backup: IV missing")
                
                val cipher = Cipher.getInstance(ALGORITHM)
                val spec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                FileOutputStream(destinationDbFile).use { fos ->
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
            Log.e(TAG, "Failed to decrypt backup: ${e.message}", e)
            false
        }
    }

    /**
     * Generates a random AES-256 secret key for backup encryption.
     */
    fun generateBackupKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_SIZE)
        return keyGen.generateKey()
    }
}
