package com.jeffers.notimindlite.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages backup keys via the Android KeyStore.
 *
 * Production callers MUST route through [getOrCreateKey], which derives/returns a
 * device-bound AES key. This replaces the prior Base64-in-SQLite pattern, which
 * left plaintext key material on disk.
 *
 * [encode] / [decode] remain available for the user-facing backup-key display flow
 * (the user is shown a Base64 string they can store externally). They are NOT used to
 * persist key material to the local database.
 */
object BackupKeyCodec {
    private const val KEY_ALIAS = "notimind_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_KEY_SIZE_BITS = 256

    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val existing = keyStore.getKey(KEY_ALIAS, null)
                ?: error("KeyStore reports alias '$KEY_ALIAS' present but returned null key")
            return existing as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        // KeyGenParameterSpec.Builder(alias, purposes) is available since API 23 (M);
        // the static `.builder(...)` factory was only added in API 31. We target minSdk 26.
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** Encodes a SecretKey's raw bytes as Base64 for user-facing display (not for DB storage). */
    fun encode(key: SecretKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    /** Decodes a Base64 string produced by [encode] back into a SecretKey. */
    fun decode(base64Key: String): SecretKey =
        SecretKeySpec(Base64.decode(base64Key, Base64.NO_WRAP), "AES")
}
