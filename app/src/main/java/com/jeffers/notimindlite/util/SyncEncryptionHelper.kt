package com.jeffers.notimindlite.util

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SyncEncryptionHelper provides authenticated encryption (AES-GCM) for cloud sync.
 * It ensures that PII is never sent to the backend in plaintext.
 */
object SyncEncryptionHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * Encrypts plaintext using the provided secret key.
     * The IV is prepended to the ciphertext for storage.
     */
    fun encrypt(plaintext: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Combine IV and ciphertext into one byte array
        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
            
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts ciphertext that has a prepended IV.
     */
    fun decrypt(encryptedBase64: String, secretKey: SecretKey): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < IV_LENGTH) throw IllegalArgumentException("Invalid encrypted data: too short")
        
        val iv = combined.sliceArray(0 until IV_LENGTH)
        val ciphertext = combined.sliceArray(IV_LENGTH until combined.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plaintext = cipher.doFinal(ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
}
