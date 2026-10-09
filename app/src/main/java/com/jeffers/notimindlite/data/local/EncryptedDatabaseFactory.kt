package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.Build
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Creates Room's SQLCipher open-helper factory for one database identity. */
object EncryptedDatabaseFactory {
    @Volatile
    private var sqlCipherLoaded = false

    fun openHelperFactory(context: Context, databaseName: String): SupportSQLiteOpenHelper.Factory? {
        if (Build.FINGERPRINT == "robolectric" || isRobolectricRuntime() || !isAndroidRuntime()) return null
        val preferences = PreferencesRepository(context.applicationContext)
        if (!preferences.dbEncrypted.value || preferences.dbEncryptionMode.value == DbEncryptionMode.NONE) return null
        ensureSqlCipherLoaded()
        val useKeystore = preferences.useKeystore.value && preferences.dbEncryptionMode.value == DbEncryptionMode.KEYSTORE
        val passphrase = SqlCipherKeyManager.getOrCreatePassphrase(context, databaseName, useKeystore)
        // SupportOpenHelperFactory retains the supplied array until the helper opens.
        // Copy before clearing the caller-owned array; clearing this same array breaks
        // database initialization when Room opens the helper after builder creation.
        val factoryPassphrase = passphrase.copyOf()
        passphrase.fill(0)
        return SupportOpenHelperFactory(factoryPassphrase)
    }

    @Synchronized
    private fun ensureSqlCipherLoaded() {
        if (!sqlCipherLoaded) {
            System.loadLibrary("sqlcipher")
            sqlCipherLoaded = true
        }
    }

    private fun isAndroidRuntime(): Boolean =
        System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true

    private fun isRobolectricRuntime(): Boolean = try {
        Class.forName("org.robolectric.RuntimeEnvironment")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
