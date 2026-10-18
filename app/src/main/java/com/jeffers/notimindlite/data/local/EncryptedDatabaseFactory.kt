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
        val preferences = PreferencesRepository(context.applicationContext)
        return when {
            Build.FINGERPRINT == "robolectric" || isRobolectricRuntime() || !isAndroidRuntime() -> null
            !preferences.securityManager.dbEncrypted.value ||
                preferences.securityManager.dbEncryptionMode.value == DbEncryptionMode.NONE -> null
            else -> createFactory(context, databaseName, preferences)
        }
    }

    private fun createFactory(
        context: Context,
        databaseName: String,
        preferences: PreferencesRepository
    ): SupportSQLiteOpenHelper.Factory {
        ensureSqlCipherLoaded()
        val useKeystore = preferences.securityManager.useKeystore.value &&
            preferences.securityManager.dbEncryptionMode.value == DbEncryptionMode.KEYSTORE
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
