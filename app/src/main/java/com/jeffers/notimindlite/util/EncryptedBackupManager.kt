package com.jeffers.notimindlite.util

import android.content.Context
import android.util.Log
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.BackupKeyCodec
import com.jeffers.notimindlite.data.local.BackupRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles creation and restoration of encrypted backups.
 *
 * ## Architecture (Option B — Two-Tier Key Wrapping)
 *
 * - A Data Encryption Key (DEK, AES-256) encrypts the SQLite database via AES-GCM.
 * - When a passphrase is provided, the DEK is wrapped under a Key Encryption Key (KEK)
 *   derived via PBKDF2-HMAC-SHA256 (600k iterations) and embedded in the file's [BackupFileFormat]
 *   header.
 * - Restores try the passphrase-wrapped DEK first (if passphrase provided), falling back
 *   to the device KeyStore key (same device).
 * - Cross-device restores require only the passphrase; no device-bound keys or audit logs
 *   need to match on the receiving device.
 */
object EncryptedBackupManager {
    private const val TAG = "EncryptedBackupManager"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = BackupFileFormat.IV_LEN
    private const val BUFFER_SIZE = 8192

    @Suppress("LongParameterList", "ReturnCount")
    suspend fun createAuthorizedBackup(
        context: Context,
        sourceDbFile: File,
        destinationFile: File,
        secretKey: SecretKey,
        encryptionKeyBase64: String? = null,
        passphrase: CharArray? = null,
    ): Boolean {
        if (!sourceDbFile.exists()) return false

        return try {
            val encSuccess = performEncryption(sourceDbFile, destinationFile, secretKey, passphrase)
            if (!encSuccess) return false

            val fileHash = calculateFileHash(destinationFile)
            val signature = generateSecureSignature(fileHash, secretKey, context)

            val record = BackupRecord(
                actionType = "EXPORT",
                fileHash = fileHash,
                signature = signature,
                fileName = destinationFile.name,
                logMessage = if (passphrase != null) "Passphrase-wrapped export" else "KeyStore-bound export",
                encryptionKeyBase64 = encryptionKeyBase64
            )

            AppDatabase.getDatabase(context).backupDao().insertRecord(record)
            AuditLogger.logBackupEvent(context, record)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Authorized backup failed: Security operation error", e)
            false
        }
    }

    @Suppress("ReturnCount")
    suspend fun restoreAuthorizedBackup(
        context: Context,
        sourceBackupFile: File,
        destinationDbFile: File,
        secretKey: SecretKey,
        passphrase: CharArray? = null,
    ): Boolean {
        if (!sourceBackupFile.exists()) return false

        return try {
            val fileBytes = sourceBackupFile.readBytes()
            val fileHash = calculateFileHash(sourceBackupFile)

            // Resolve DEK: unwrap from passphrase if provided and file has wrapped header,
            // otherwise use caller-provided secretKey (KeyStore / fallback).
            val resolvedDek = resolveDekForRestore(fileBytes, secretKey, passphrase)
                ?: return false

            // Verify authorization:
            // - Same device: match local DB or persistent signed audit log.
            // - Cross-device / post-uninstall: if passphrase successfully unwrapped the DEK,
            //   possession of the passphrase proves authorization; local log match is not required.
            val localRecord = findLocalAuthorizationRecord(context, fileHash)
            val isPassphraseAuthorized = passphrase != null && isFilePassphraseWrapped(fileBytes)

            if (localRecord == null && !isPassphraseAuthorized) {
                Log.e(TAG, "Unauthorized backup attempt: Hash $fileHash not found in local log")
                return false
            }

            val success = performDecryption(fileBytes, destinationDbFile, resolvedDek)
            if (success) {
                val importRecord = BackupRecord(
                    actionType = "IMPORT",
                    fileHash = fileHash,
                    signature = localRecord?.signature ?: "PASSPHRASE_VERIFIED",
                    fileName = sourceBackupFile.name,
                    logMessage = if (isPassphraseAuthorized) {
                        "Successfully imported cross-device backup via passphrase"
                    } else {
                        "Successfully imported authorized backup via local signature"
                    }
                )
                AppDatabase.getDatabase(context).backupDao().insertRecord(importRecord)
                AuditLogger.logBackupEvent(context, importRecord)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Authorized restore failed: ${e.message}", e)
            false
        }
    }

    private suspend fun findLocalAuthorizationRecord(context: Context, fileHash: String): BackupRecord? {
        val record = AppDatabase.getDatabase(context).backupDao().getRecordByHash(fileHash)
        if (record != null) return record

        val persistentEntry = AuditLogger.readAndVerifyPersistentLogs(context)
            .find { it.details.contains(fileHash) && it.isValid }
        return persistentEntry?.let {
            BackupRecord(
                actionType = "EXPORT",
                fileHash = fileHash,
                signature = it.signature,
                fileName = "",
                logMessage = "Verified via persistent on-disk signed log"
            )
        }
    }

    private fun isFilePassphraseWrapped(bytes: ByteArray): Boolean =
        try {
            BackupFileFormat.parse(bytes).keySource == BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED
        } catch (_: Exception) {
            false
        }

    @Suppress("ReturnCount")
    private fun resolveDekForRestore(
        bytes: ByteArray,
        fallbackKey: SecretKey,
        passphrase: CharArray?,
    ): SecretKey? {
        val header = try {
            BackupFileFormat.parse(bytes)
        } catch (_: Exception) {
            null
        }

        if (header == null) {
            // Legacy format: raw [12B IV][ciphertext] without NMB1 header. Use fallback key.
            return fallbackKey
        }

        if (header.keySource == BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED) {
            if (passphrase == null) {
                Log.w(TAG, "Backup is passphrase-wrapped but no passphrase was provided")
                return null
            }
            return try {
                val kek = BackupKeyWrap.deriveKek(
                    passphrase = passphrase,
                    salt = header.pbkdf2Salt,
                    iterations = header.pbkdf2Iterations,
                )
                BackupKeyWrap.unwrapDek(header.wrappedDek, header.kekIv, kek)
            } catch (e: AEADBadTagException) {
                Log.e(TAG, "Passphrase incorrect; failed to unwrap DEK", e)
                null
            }
        }

        return fallbackKey
    }

    @Suppress("NestedBlockDepth")
    private fun performEncryption(
        source: File,
        dest: File,
        key: SecretKey,
        passphrase: CharArray?,
    ): Boolean {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val dataIv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, dataIv))

            val cipherOut = ByteArrayOutputStream()
            FileInputStream(source).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val output = cipher.update(buffer, 0, bytesRead)
                    if (output != null) cipherOut.write(output)
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null) cipherOut.write(finalBlock)
            }

            val ciphertext = cipherOut.toByteArray()

            if (passphrase != null) {
                val salt = BackupKeyWrap.generateSalt()
                val kek = BackupKeyWrap.deriveKek(passphrase, salt)
                val (kekIv, wrappedDek) = BackupKeyWrap.wrapDek(key, kek)
                val wrappedBytes = BackupFileFormat.writeWrapped(
                    BackupFileFormat.WrappedPayload(
                        salt = salt,
                        iterations = BackupFileFormat.PBKDF2_ITERATIONS,
                        kekIv = kekIv,
                        wrappedDek = wrappedDek,
                        dataIv = dataIv,
                        ciphertext = ciphertext,
                    )
                )
                dest.writeBytes(wrappedBytes)
            } else {
                FileOutputStream(dest).use { fos ->
                    fos.write(dataIv)
                    fos.write(ciphertext)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            false
        }
    }

    private fun performDecryption(bytes: ByteArray, dest: File, key: SecretKey): Boolean {
        return try {
            val header = try {
                BackupFileFormat.parse(bytes)
            } catch (_: Exception) {
                null
            }

            val (iv, ciphertext) = if (header != null) {
                Pair(header.dataIv, header.ciphertext)
            } else {
                if (bytes.size < IV_LENGTH) return false
                val iv = bytes.copyOfRange(0, IV_LENGTH)
                val ct = bytes.copyOfRange(IV_LENGTH, bytes.size)
                Pair(iv, ct)
            }

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            val plaintext = cipher.doFinal(ciphertext)
            dest.writeBytes(plaintext)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            false
        }
    }

    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun generateSecureSignature(
        hash: String,
        secretKey: SecretKey,
        context: Context,
    ): String {
        return try {
            BackupNotaryClient.getSignature(context, hash)
        } catch (e: Exception) {
            Log.e(TAG, "Remote notary signing failed", e)
            throw e
        }
    }
}

/**
 * Production key source. Returns the device-bound AES key from the Android KeyStore.
 *
 * The [context] parameter is intentionally unused today but is part of the public API:
 * a future enhancement may route through [androidx.security.crypto.EncryptedSharedPreferences]
 * or a Context-bound key-wrapping helper, at which point production callers do not need
 * to migrate. Tests must use the no-arg overload below, which uses plain JCE.
 */
@Suppress("UnusedParameter")
fun generateBackupKey(context: Context): SecretKey =
    BackupKeyCodec.getOrCreateKey()

/**
 * Test-only convenience: returns a freshly generated AES-256 SecretKey via JCE.
 * Avoids the Android KeyStore (not available in unit tests on the host JVM).
 * Production code MUST use the [Context]-receiving overload above so keys are
 * hardware-bound and never stored on disk.
 */
fun generateBackupKey(): SecretKey =
    KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
