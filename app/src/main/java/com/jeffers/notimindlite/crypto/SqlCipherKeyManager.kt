package com.jeffers.notimindlite.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap

/** Provides a stable SQLCipher passphrase encrypted by an Android Keystore key. */
object SqlCipherKeyManager {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val PASSPHRASE_BYTES = 32
    private const val PREFS_PREFIX = "notimind_sqlcipher_key_"
    private const val VALUE = "encrypted_passphrase"
    private val robolectricKeys = ConcurrentHashMap<String, SecretKey>()

    fun getOrCreatePassphrase(context: Context, databaseName: String): ByteArray {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_PREFIX + databaseName, Context.MODE_PRIVATE)
        val encoded = prefs.getString(VALUE, null)
        if (encoded != null) {
            return decrypt(appContext, databaseName, Base64.decode(encoded, Base64.NO_WRAP))
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val encrypted = encrypt(appContext, databaseName, passphrase)
        val encodedPassphrase = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit(commit = true) {
            putString(VALUE, encodedPassphrase)
        }
        check(prefs.getString(VALUE, null) == encodedPassphrase) {
            "Unable to persist SQLCipher passphrase"
        }
        return passphrase
    }

    private fun keyFor(context: Context, databaseName: String): SecretKey {
        val alias = "notimind_sqlcipher_$databaseName"
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE)
            generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generator.generateKey()
        } catch (e: Exception) {
            check(Build.FINGERPRINT == "robolectric") {
                "AndroidKeyStore is required for SQLCipher passphrase protection"
            }
            robolectricKeys.getOrPut(alias) {
                SecretKeySpec(ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }, KEY_ALGORITHM)
            }
        }
    }

    private fun encrypt(context: Context, databaseName: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(context, databaseName))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(IV_BYTES + ciphertext.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(context: Context, databaseName: String, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "Invalid SQLCipher key payload" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyFor(context, databaseName), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
