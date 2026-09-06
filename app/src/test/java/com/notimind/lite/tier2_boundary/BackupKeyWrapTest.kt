@file:Suppress("PackageNaming")

package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.util.BackupFileFormat
import com.jeffers.notimindlite.util.BackupKeyWrap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class BackupKeyWrapTest {

    private fun generateDek(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `wrap and unwrap DEK roundtrip succeeds with correct passphrase`() {
        val dek = generateDek()
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val salt = BackupKeyWrap.generateSalt()

        // Derive KEK with lower iterations for fast testing
        val kek = BackupKeyWrap.deriveKek(passphrase, salt, iterations = 1000)
        val (kekIv, wrappedDek) = BackupKeyWrap.wrapDek(dek, kek)

        val unwrappedDek = BackupKeyWrap.unwrapDek(wrappedDek, kekIv, kek)

        assertArrayEquals(
            "Unwrapped key bytes must match original DEK",
            dek.encoded,
            unwrappedDek.encoded
        )
    }

    @Test
    fun `unwrap fails with AEADBadTagException when wrong passphrase is used`() {
        val dek = generateDek()
        val correctPass = "super-secret-passphrase".toCharArray()
        val wrongPass = "wrong-passphrase".toCharArray()
        val salt = BackupKeyWrap.generateSalt()

        val kek = BackupKeyWrap.deriveKek(correctPass, salt, iterations = 1000)
        val (kekIv, wrappedDek) = BackupKeyWrap.wrapDek(dek, kek)

        val wrongKek = BackupKeyWrap.deriveKek(wrongPass, salt, iterations = 1000)

        assertThrows(AEADBadTagException::class.java) {
            BackupKeyWrap.unwrapDek(wrappedDek, kekIv, wrongKek)
        }
    }

    @Test
    fun `unwrap fails when wrapped payload is tampered`() {
        val dek = generateDek()
        val passphrase = "test-passphrase".toCharArray()
        val salt = BackupKeyWrap.generateSalt()

        val kek = BackupKeyWrap.deriveKek(passphrase, salt, iterations = 1000)
        val (kekIv, wrappedDek) = BackupKeyWrap.wrapDek(dek, kek)

        val tamperedBytes = wrappedDek.clone()
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes[tamperedBytes.size - 1].toInt() xor 0xFF).toByte()

        assertThrows(AEADBadTagException::class.java) {
            BackupKeyWrap.unwrapDek(tamperedBytes, kekIv, kek)
        }
    }

    @Test
    fun `BackupFileFormat writeWrapped and parse roundtrip succeeds`() {
        val salt = ByteArray(16) { 0x01 }
        val kekIv = ByteArray(12) { 0x02 }
        val wrappedDek = ByteArray(48) { 0x03 }
        val dataIv = ByteArray(12) { 0x04 }
        val ciphertext = ByteArray(32) { 0x05 }

        val payload = BackupFileFormat.WrappedPayload(
            salt = salt,
            iterations = 1000,
            kekIv = kekIv,
            wrappedDek = wrappedDek,
            dataIv = dataIv,
            ciphertext = ciphertext
        )

        val serialized = BackupFileFormat.writeWrapped(payload)
        val parsed = BackupFileFormat.parse(serialized)

        assertEquals(BackupFileFormat.CURRENT_VERSION, parsed.version)
        assertEquals(BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED, parsed.keySource)
        assertEquals(1000, parsed.pbkdf2Iterations)
        assertArrayEquals(salt, parsed.pbkdf2Salt)
        assertArrayEquals(kekIv, parsed.kekIv)
        assertArrayEquals(wrappedDek, parsed.wrappedDek)
        assertArrayEquals(dataIv, parsed.dataIv)
        assertArrayEquals(ciphertext, parsed.ciphertext)
    }

    @Test
    fun `parse fails when magic bytes are invalid`() {
        val badBytes = "CORRUPT_HEADER_FOR_TESTING".toByteArray()
        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(badBytes)
        }
        assertTrue(exception.message?.contains("Not a NotiMind backup file") == true)
    }

    @Test
    fun `parse fails when file is truncated`() {
        val truncated = ByteArray(4) { 0x00 }
        assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(truncated)
        }
    }
}
