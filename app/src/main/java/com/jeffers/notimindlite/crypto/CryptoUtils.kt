package com.jeffers.notimindlite.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Small, well-tested crypto primitives used by Phase 2 keystore/backup helpers.
 * - HKDF-SHA256 (RFC 5869) extraction+expand
 * - AES-256-GCM encrypt/decrypt with 12-byte nonce
 * Implementation is intentionally minimal and uses standard JCE primitives so
 * it runs on the JVM for unit tests and on Android at runtime.
 */
object CryptoUtils {
    private val RNG = SecureRandom()
    private const val HKDF_HASH = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_LEN = 12 // 96 bits

    /** Derive key material using HKDF-SHA256. Returns exactly length bytes. */
    fun hkdfSha256ExtractAndExpand(salt: ByteArray?, ikm: ByteArray, info: ByteArray?, length: Int): ByteArray {
        // HKDF-Extract
        // RFC 5869 specifies an all-zero hash-length salt when no salt is supplied.
        val prk = hmacSha256(salt ?: ByteArray(32), ikm)
        // HKDF-Expand
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        val okm = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        for (i in 1..n) {
            val mac = Mac.getInstance(HKDF_HASH)
            mac.init(SecretKeySpec(prk, HKDF_HASH))
            mac.update(previous)
            if (info != null) mac.update(info)
            mac.update(i.toByte())
            val t = mac.doFinal()
            val toCopy = minOf(t.size, length - written)
            System.arraycopy(t, 0, okm, written, toCopy)
            written += toCopy
            previous = t
        }
        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HKDF_HASH)
        mac.init(SecretKeySpec(key, HKDF_HASH))
        return mac.doFinal(data)
    }

    /** AES-256-GCM encrypt. Returns iv||ciphertext (iv length = 12 bytes). */
    fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AES-256 key required (32 bytes)" }
        val iv = ByteArray(GCM_IV_LEN)
        RNG.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM)
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        if (aad != null) cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** AES-256-GCM decrypt. Accepts iv||ciphertext and returns plaintext. */
    fun aesGcmDecrypt(key: ByteArray, ivAndCiphertext: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AES-256 key required (32 bytes)" }
        require(ivAndCiphertext.size > GCM_IV_LEN) { "ciphertext too short" }
        val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LEN)
        val ct = ivAndCiphertext.copyOfRange(GCM_IV_LEN, ivAndCiphertext.size)
        val cipher = Cipher.getInstance(AES_GCM)
        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ct)
    }
}
