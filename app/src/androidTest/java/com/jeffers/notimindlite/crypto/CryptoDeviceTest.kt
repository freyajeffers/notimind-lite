package com.jeffers.notimindlite.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException

@RunWith(AndroidJUnit4::class)
class CryptoDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun keyManager_getOrCreateMasterKeyAlias_returnsValidAliasOnDevice() {
        val defaultAlias = KeyManager.getOrCreateMasterKeyAlias(context, null)
        assertNotNull(defaultAlias)
        assertTrue(defaultAlias.isNotBlank())

        val customAlias = KeyManager.getOrCreateMasterKeyAlias(context, "custom_alias_test")
        assertNotNull(customAlias)
        assertTrue(customAlias.isNotBlank())
    }

    @Test
    fun cryptoUtils_aesGcmEncryptAndDecrypt_withAadRoundTripOnDevice() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val plaintext = "Sensitive device token payload for Android 16".toByteArray(Charsets.UTF_8)
        val aad = "DeviceAAD_Metadata".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoUtils.aesGcmEncrypt(key, plaintext, aad)
        // 12 bytes IV + plaintext length + 16 bytes GCM tag
        assertEquals(12 + plaintext.size + 16, encrypted.size)

        val decrypted = CryptoUtils.aesGcmDecrypt(key, encrypted, aad)
        assertArrayEquals(plaintext, decrypted)
        assertEquals("Sensitive device token payload for Android 16", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun cryptoUtils_aesGcmDecrypt_tamperedCiphertextThrowsOnDevice() {
        val key = ByteArray(32) { (it + 5).toByte() }
        val plaintext = "Integrity check test".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoUtils.aesGcmEncrypt(key, plaintext, null)
        val tampered = encrypted.copyOf()
        // Flip bit in the ciphertext / auth tag portion
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            CryptoUtils.aesGcmDecrypt(key, tampered, null)
        }
    }

    @Test
    fun cryptoUtils_aesGcmDecrypt_wrongAadThrowsOnDevice() {
        val key = ByteArray(32) { (it + 7).toByte() }
        val plaintext = "AAD sensitivity test".toByteArray(Charsets.UTF_8)
        val aad1 = "Correct_AAD".toByteArray(Charsets.UTF_8)
        val aad2 = "Wrong_AAD".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoUtils.aesGcmEncrypt(key, plaintext, aad1)

        assertThrows(AEADBadTagException::class.java) {
            CryptoUtils.aesGcmDecrypt(key, encrypted, aad2)
        }
    }

    @Test
    fun cryptoUtils_hkdfExtractAndExpand_derivesExpectedKeyLengthOnDevice() {
        val ikm = "InputKeyMaterialOnHardware".toByteArray(Charsets.UTF_8)
        val salt = "DeviceSaltVal".toByteArray(Charsets.UTF_8)
        val info = "DeviceContextInfo".toByteArray(Charsets.UTF_8)

        val derivedKey32 = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, info, 32)
        assertEquals(32, derivedKey32.size)

        val derivedKey64 = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, info, 64)
        assertEquals(64, derivedKey64.size)

        // Derivation must be deterministic
        val derivedKey32Repeat = CryptoUtils.hkdfSha256ExtractAndExpand(salt, ikm, info, 32)
        assertArrayEquals(derivedKey32, derivedKey32Repeat)
    }

    @Test
    fun encryptedStorage_masterSecretDerivationAndEncryptDecryptOnDevice() {
        val masterSecret = EncryptedStorage.generateMasterSecret()
        assertEquals(32, masterSecret.size)

        val secretPayload = "Database master encryption passphrase".toByteArray(Charsets.UTF_8)
        val encryptedPayload = EncryptedStorage.encrypt(masterSecret, secretPayload)

        val decryptedPayload = EncryptedStorage.decrypt(masterSecret, encryptedPayload)
        assertArrayEquals(secretPayload, decryptedPayload)
        assertEquals("Database master encryption passphrase", String(decryptedPayload, Charsets.UTF_8))

        // Different master secrets produce distinct key derivations
        val anotherMasterSecret = EncryptedStorage.generateMasterSecret()
        assertFalse(masterSecret.contentEquals(anotherMasterSecret))

        assertThrows(AEADBadTagException::class.java) {
            EncryptedStorage.decrypt(anotherMasterSecret, encryptedPayload)
        }
    }
}
