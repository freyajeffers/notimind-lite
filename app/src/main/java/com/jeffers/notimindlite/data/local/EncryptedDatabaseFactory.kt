package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.Build
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** Creates Room's SQLCipher open-helper factory for one database identity. */
object EncryptedDatabaseFactory {
    fun openHelperFactory(context: Context, databaseName: String): SupportSQLiteOpenHelper.Factory? {
        if (Build.FINGERPRINT == "robolectric") return null
        if (isPlaintextDatabase(context.getDatabasePath(databaseName))) return null
        ensureNativeLibraryLoaded()
        val passphrase = SqlCipherKeyManager.getOrCreatePassphrase(context, databaseName)
        return SupportOpenHelperFactory(passphrase).also {
            passphrase.fill(0)
        }
    }

    @Synchronized
    private fun ensureNativeLibraryLoaded() {
        if (nativeLoaded) return
        try {
            System.loadLibrary("sqlcipher")
            nativeLoaded = true
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("SQLCipher native library could not be loaded", error)
        }
    }

    private var nativeLoaded = false

    private fun isPlaintextDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        return file.inputStream().use { input -> input.readNBytes(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER) }
    }

    private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
}
