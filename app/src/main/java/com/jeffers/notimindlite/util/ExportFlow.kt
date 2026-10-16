package com.jeffers.notimindlite.util

import android.content.Context
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.domain.backup.EncryptedBackupManager
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import java.io.File
import javax.crypto.spec.SecretKeySpec

internal object ExportFlow {

    internal fun encryptExport(payload: ByteArray, context: Context, passphrase: CharArray?): ByteArray {
        val salt = if (passphrase != null) BackupKeyWrap.generateSalt() else ByteArray(0)
        val key = if (passphrase != null) {
            BackupKeyWrap.deriveKek(passphrase, salt)
        } else {
            SecretKeySpec(SqlCipherKeyManager.getOrCreatePassphrase(context, "exports"), "AES")
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val header = byteArrayOf(
            'N'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte(), salt.size.toByte()
        )
        return header + salt + cipher.iv + cipher.doFinal(payload)
    }

    suspend fun performEncryptedBackup(
        context: Context,
        secretKey: javax.crypto.SecretKey,
        passphrase: CharArray?
    ): Result<File> {
        return try {
            val preferences = PreferencesRepository(context.applicationContext)
            require(!preferences.securityManager.requirePassphrase.value || passphrase != null && passphrase.isNotEmpty()) {
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
