package com.jeffers.notimindlite.crypto

import java.security.SecureRandom

/**
 * EncryptedStorage provides a small abstraction for deriving an AES-256-GCM key
 * from a master secret using HKDF-SHA256 and performing authenticated encryption.
 *
 * Intended for use by keystore/backup helpers. The masterSecret represents a
 * high-entropy key material (e.g. exported keystore secret or derived from a
 * hardware-backed MasterKey). The implementation is intentionally small and
 * JVM-testable.
 */
object EncryptedStorage {
    private val RNG = SecureRandom()
    private const val DERIVE_INFO = "NotiMind-Lite Storage Key"

    /**
     * Derive a 32-byte AES-256 key from supplied master secret using HKDF-SHA256.
     */
    fun deriveAesKey(masterSecret: ByteArray, salt: ByteArray? = null, info: ByteArray? = null): ByteArray {
        val infoBytes = info ?: DERIVE_INFO.toByteArray(Charsets.UTF_8)
        return CryptoUtils.hkdfSha256ExtractAndExpand(salt, masterSecret, infoBytes, 32)
    }

    /**
     * Encrypt plaintext with a derived key from masterSecret. Returns iv||ciphertext.
     */
    fun encrypt(masterSecret: ByteArray, plaintext: ByteArray, aad: ByteArray? = null, salt: ByteArray? = null): ByteArray {
        val key = deriveAesKey(masterSecret, salt)
        return CryptoUtils.aesGcmEncrypt(key, plaintext, aad)
    }

    /**
     * Decrypt iv||ciphertext with a derived key from masterSecret. Returns plaintext or throws on auth failure.
     */
    fun decrypt(masterSecret: ByteArray, ivAndCiphertext: ByteArray, aad: ByteArray? = null, salt: ByteArray? = null): ByteArray {
        val key = deriveAesKey(masterSecret, salt)
        return CryptoUtils.aesGcmDecrypt(key, ivAndCiphertext, aad)
    }

    /**
     * Utility: generate a random 32-byte master secret.
     */
    fun generateMasterSecret(): ByteArray {
        val b = ByteArray(32)
        RNG.nextBytes(b)
        return b
    }
}
