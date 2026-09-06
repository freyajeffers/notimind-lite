package com.jeffers.notimindlite.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key-wrapping utilities for encrypting/decrypting the backup's Data Encryption Key (DEK)
 * using a user-supplied passphrase.
 *
 * ## Architecture
 *
 * ```
 * user passphrase
 *       │
 *       ▼  (PBKDF2-HMAC-SHA256, 16-byte random salt, 600,000 iterations)
 * Key Encryption Key (KEK, 256 bits)
 *       │
 *       ▼  (AES-GCM-256 with 12-byte random IV)
 * wrap(DEK)  ──►  persisted in backup header
 * ```
 *
 * The wrapped DEK is 48 bytes (32 bytes raw AES key + 16 bytes GCM auth tag).
 * It is embedded directly in the backup file header so the file is self-contained.
 *
 * The DEK itself is generated either in the Android KeyStore or freshly via JCE.
 * Once wrapped, it can be unwrapped on ANY machine running this code given the
 * same passphrase — solving the cross-device and reinstall-restore problem.
 */
internal object BackupKeyWrap {
    private const val KEK_ALGORITHM = "AES"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEK_KEY_SIZE_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    /** Generates a fresh 16-byte random salt for PBKDF2. */
    fun generateSalt(): ByteArray =
        ByteArray(BackupFileFormat.PBKDF2_SALT_LEN).also { secureRandom.nextBytes(it) }

    /** Generates a fresh 12-byte random IV for AES-GCM. */
    fun generateIv(): ByteArray =
        ByteArray(BackupFileFormat.IV_LEN).also { secureRandom.nextBytes(it) }

    /**
     * Derives a Key Encryption Key (KEK) from a passphrase using PBKDF2-HMAC-SHA256.
     * The returned SecretKey is ephemeral — never persisted.
     */
    fun deriveKek(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = BackupFileFormat.PBKDF2_ITERATIONS,
    ): SecretKey {
        require(salt.size == BackupFileFormat.PBKDF2_SALT_LEN) {
            "Salt must be ${BackupFileFormat.PBKDF2_SALT_LEN} bytes"
        }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEK_KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, KEK_ALGORITHM)
    }

    /**
     * Wraps a Data Encryption Key (DEK) under a passphrase-derived KEK using AES-GCM.
     *
     * @return Pair of (kekIv, wrappedBytes). The KEK IV must be stored alongside the wrapped bytes.
     */
    fun wrapDek(dek: SecretKey, kek: SecretKey): Pair<ByteArray, ByteArray> {
        val kekIv = generateIv()
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, kekIv))
        val wrapped = cipher.doFinal(dek.encoded)
        return Pair(kekIv, wrapped)
    }

    /**
     * Unwraps a Data Encryption Key (DEK) using the passphrase-derived KEK.
     *
     * Throws [javax.crypto.AEADBadTagException] if the passphrase is wrong or the
     * wrapped bytes were tampered with.
     */
    fun unwrapDek(wrappedDek: ByteArray, kekIv: ByteArray, kek: SecretKey): SecretKey {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, kekIv))
        val rawDek = cipher.doFinal(wrappedDek)
        return SecretKeySpec(rawDek, "AES")
    }
}
