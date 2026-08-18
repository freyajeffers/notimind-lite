package com.jeffers.notimindlite.data.local

import android.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Utility to convert SecretKeys to Base64 strings and back for DB storage.
 */
object BackupKeyCodec {
    fun encode(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun decode(base64Key: String): SecretKey {
        val decodedKey = Base64.decode(base64Key, Base64.NO_WRAP)
        return SecretKeySpec(decodedKey, "AES")
    }
}
