package com.jeffers.notimindlite.crypto

import android.content.Context
import androidx.security.crypto.MasterKeys

/**
 * Abstraction for creating/retrieving MasterKey aliases for AndroidKeyStore-backed keys.
 */
object KeyManager {
    fun getOrCreateMasterKeyAlias(context: Context?, alias: String?): String {
        // Use AndroidX Security's MasterKey.Builder when available
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return masterKeyAlias
    }
}
