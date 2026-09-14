package com.jeffers.notimindlite.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoUtilsTest {

    @Test
    fun `aes gcm encrypt and decrypt round trip with correct key`() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val plaintext = "Sensitive notification content: OTP 123456".toByteArray(Charsets.UTF_8)
        val aad = "com.example.app|12345678".toByteArray(Charsets.UTF_8)

        val ciphertextWithIv = CryptoUtils.aesGcmEncrypt(key, plaintext, aad)
        assertNotNull(ciphertextWithIv)
        assertTrue(ciphertextWithIv.size > 12)

        val decrypted = CryptoUtils.aesGcmDecrypt(key, ciphertextWithIv, aad)
        assertArrayEquals(plaintext, decrypted)
        assertEquals("Sensitive notification content: OTP 123456", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `aes gcm encryption produces unique IV on subsequent calls`() {
        val key = ByteArray(32) { 42.toByte() }
        val plaintext = "Fixed plaintext for IV randomness check".toByteArray(Charsets.UTF_8)

        val ct1 = CryptoUtils.aesGcmEncrypt(key, plaintext)
        val ct2 = CryptoUtils.aesGcmEncrypt(key, plaintext)

        assertFalse(ct1.contentEquals(ct2))
        val iv1 = ct1.copyOfRange(0, 12)
        val iv2 = ct2.copyOfRange(0, 12)
        assertFalse(iv1.contentEquals(iv2))
    }

    @Test(expected = AEADBadTagException::class)
    fun `aes gcm decrypt fails on tampered ciphertext`() {
        val key = ByteArray(32) { 7.toByte() }
        val plaintext = "Tamper test payload".toByteArray(Charsets.UTF_8)

        val ciphertextWithIv = CryptoUtils.aesGcmEncrypt(key, plaintext)
        val tampered = ciphertextWithIv.copyOf()
        // Flip a byte in the ciphertext body/tag
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        CryptoUtils.aesGcmDecrypt(key, tampered)
    }

    @Test(expected = AEADBadTagException::class)
    fun `aes gcm decrypt fails with mismatched AAD`() {
        val key = ByteArray(32) { 9.toByte() }
        val plaintext = "AAD mismatch test".toByteArray(Charsets.UTF_8)
        val correctAad = "valid_aad".toByteArray(Charsets.UTF_8)
        val wrongAad = "tampered_aad".toByteArray(Charsets.UTF_8)

        val ciphertextWithIv = CryptoUtils.aesGcmEncrypt(key, plaintext, correctAad)
        CryptoUtils.aesGcmDecrypt(key, ciphertextWithIv, wrongAad)
    }

    @Test
    fun `hkdf sha256 produces deterministic output matching RFC parameters`() {
        val ikm = "input-keying-material".toByteArray(Charsets.UTF_8)
        val salt = "salt-value-1234".toByteArray(Charsets.UTF_8)
        val info = "info-context".toByteArray(Charsets.UTF_8)

        val out1 = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, info, 32)
        val out2 = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, info, 32)

        assertEquals(32, out1.size)
        assertArrayEquals(out1, out2)

        // Changing info must yield different key
        val outDifferentInfo = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, "different-info".toByteArray(Charsets.UTF_8), 32)
        assertFalse(out1.contentEquals(outDifferentInfo))
    }
}
