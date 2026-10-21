package com.jeffers.notimindlite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SyncEncryptionHelperTest {
    private lateinit var primaryKey: SecretKey
    private lateinit var secondaryKey: SecretKey

    @Before
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        primaryKey = keyGen.generateKey()
        secondaryKey = keyGen.generateKey()
    }

    @Test
    fun encryptAndDecrypt_roundTrip_restoresOriginalPlaintext() {
        val samples = listOf(
            "Hello World!",
            "",
            "A quick brown fox jumps over 12345 lazy dogs?!",
            "Multi\nline\ntext\twith special chars: \uD83D\uDD12 \uD83D\uDE80 \u2705",
            "Very long notification content ".repeat(50)
        )

        for (sample in samples) {
            val encrypted = SyncEncryptionHelper.encrypt(sample, primaryKey)
            assertTrue(encrypted.isNotBlank())
            val decrypted = SyncEncryptionHelper.decrypt(encrypted, primaryKey)
            assertEquals(sample, decrypted)
        }
    }

    @Test
    fun encrypt_generatesUniqueCiphertexts_forSamePlaintext() {
        val plaintext = "Consistent notification message payload"
        val cipher1 = SyncEncryptionHelper.encrypt(plaintext, primaryKey)
        val cipher2 = SyncEncryptionHelper.encrypt(plaintext, primaryKey)

        assertNotEquals(cipher1, cipher2)
        assertEquals(plaintext, SyncEncryptionHelper.decrypt(cipher1, primaryKey))
        assertEquals(plaintext, SyncEncryptionHelper.decrypt(cipher2, primaryKey))
    }

    @Test
    fun decrypt_withDifferentKey_failsVerification() {
        val plaintext = "Sensitive user notification data"
        val encrypted = SyncEncryptionHelper.encrypt(plaintext, primaryKey)

        assertThrows(GeneralSecurityException::class.java) {
            SyncEncryptionHelper.decrypt(encrypted, secondaryKey)
        }
    }

    @Test
    fun decrypt_withCorruptedPayload_failsVerification() {
        val plaintext = "Integrity protected notification"
        val encrypted = SyncEncryptionHelper.encrypt(plaintext, primaryKey)
        val rawBytes = Base64.getDecoder().decode(encrypted)

        // Flip bits in the ciphertext / auth tag portion
        rawBytes[rawBytes.size - 1] = (rawBytes[rawBytes.size - 1].toInt() xor 0xFF).toByte()
        val corruptedBase64 = Base64.getEncoder().encodeToString(rawBytes)

        assertThrows(GeneralSecurityException::class.java) {
            SyncEncryptionHelper.decrypt(corruptedBase64, primaryKey)
        }
    }

    @Test
    fun decrypt_withTooShortData_throwsIllegalArgumentException() {
        // Less than 12-byte IV
        val tooShortBytes = ByteArray(8) { 0x01 }
        val tooShortBase64 = Base64.getEncoder().encodeToString(tooShortBytes)

        assertThrows(IllegalArgumentException::class.java) {
            SyncEncryptionHelper.decrypt(tooShortBase64, primaryKey)
        }
    }

    @Test
    fun decrypt_withInvalidBase64_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncEncryptionHelper.decrypt("!!!Not-Valid-Base64###", primaryKey)
        }
    }
}
