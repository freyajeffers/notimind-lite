package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.domain.backup.EncryptedBackupManager
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import java.io.File
import javax.crypto.spec.SecretKeySpec

internal object ExportFlow {
    private const val MAX_SALT_LENGTH = 255
    private val exportMagic = byteArrayOf(
        'N'.code.toByte(),
        'M'.code.toByte(),
        'E'.code.toByte(),
        '1'.code.toByte()
    )
    private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"

    internal fun encryptExport(
        payload: ByteArray,
        context: Context,
        passphrase: CharArray?
    ): ByteArray {
        val salt = if (passphrase != null) {
            BackupKeyWrap.generateSalt()
        } else {
            ByteArray(0)
        }

        val key = if (passphrase != null) {
            BackupKeyWrap.deriveKek(passphrase, salt)
        } else {
            val pass = SqlCipherKeyManager.getOrCreatePassphrase(context, "exports")
            SecretKeySpec(pass, "AES")
        }

        val cipher = javax.crypto.Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)

        val header = buildHeader(salt)

        val encrypted = cipher.doFinal(payload)

        return header + salt + cipher.iv + encrypted
    }

    private fun buildHeader(salt: ByteArray): ByteArray {
        // Header format: 4-byte magic + 1-byte salt length
        val saltLen = salt.size.coerceAtMost(MAX_SALT_LENGTH)
        return exportMagic + byteArrayOf(saltLen.toByte())
    }

    suspend fun performEncryptedBackup(
        context: Context,
        secretKey: javax.crypto.SecretKey,
        passphrase: CharArray?
    ): Result<File> {
        return try {
            val preferences = PreferencesRepository(context.applicationContext)
            val passphraseRequired = preferences.securityManager.requirePassphrase.value
            require(!passphraseRequired || passphrase != null && passphrase.isNotEmpty()) {
                "A passphrase is required for encrypted exports"
            }
            check(NetworkUtils.isInternetAvailable(context)) {
                "Active internet connection is required to create a backup"
            }

            val dbFile = context.getDatabasePath("notifications.db")
            check(dbFile.exists()) { "Database file not found" }

            val backupFile = File(context.cacheDir, "notimind_backup_${System.currentTimeMillis()}.enc")

            val success = EncryptedBackupManager.createAuthorizedBackup(
                context = context,
                sourceDbFile = dbFile,
                destinationFile = backupFile,
                secretKey = secretKey,
                passphrase = passphrase,
            )

            check(success) { "Backup encryption or notary authorization failed" }
            Result.success(backupFile)
        } catch (e: java.io.IOException) {
            android.util.Log.e("DatabaseExporter", "Backup process failed: IO error", e)
            Result.failure(e)
        } catch (e: java.security.GeneralSecurityException) {
            android.util.Log.e("DatabaseExporter", "Backup process failed: crypto error", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            android.util.Log.e("DatabaseExporter", "Backup process failed: security exception", e)
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("DatabaseExporter", "Backup process failed: invalid argument", e)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            android.util.Log.e("DatabaseExporter", "Backup process failed: invalid state", e)
            Result.failure(e)
        }
    }
}
