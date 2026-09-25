package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.Build
import com.jeffers.notimindlite.crypto.SqlCipherKeyManager
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Creates Room's SQLCipher open-helper factory for one database identity. */
object EncryptedDatabaseFactory {
    fun openHelperFactory(context: Context, databaseName: String): SupportSQLiteOpenHelper.Factory? {
        if (Build.FINGERPRINT == "robolectric") return null
        val passphrase = SqlCipherKeyManager.getOrCreatePassphrase(context, databaseName)
        return SupportOpenHelperFactory(passphrase).also {
            passphrase.fill(0)
        }
    }
}
