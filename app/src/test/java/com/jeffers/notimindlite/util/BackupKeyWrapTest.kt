package com.jeffers.notimindlite.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class BackupKeyWrapTest {
    @Test
    fun deriveAndWrapRoundTripDecryptsExportKey() {
        val salt = ByteArray(BackupFileFormat.PBKDF2_SALT_LEN) { it.toByte() }
        val dek = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        val kek = BackupKeyWrap.deriveKek("correct horse battery staple".toCharArray(), salt, iterations = 1_000)
        val (iv, wrapped) = BackupKeyWrap.wrapDek(dek, kek)
        assertArrayEquals(dek.encoded, BackupKeyWrap.unwrapDek(wrapped, iv, kek).encoded)
    }

    @Test
    fun wrongPassphraseCannotDecryptWrappedExportKey() {
        val salt = ByteArray(BackupFileFormat.PBKDF2_SALT_LEN) { 7 }
        val dek = SecretKeySpec(ByteArray(32) { 9 }, "AES")
        val kek = BackupKeyWrap.deriveKek("correct".toCharArray(), salt, iterations = 1_000)
        val (iv, wrapped) = BackupKeyWrap.wrapDek(dek, kek)
        val wrong = BackupKeyWrap.deriveKek("wrong".toCharArray(), salt, iterations = 1_000)
        assertThrows(Exception::class.java) { BackupKeyWrap.unwrapDek(wrapped, iv, wrong) }
    }

    @Test
    fun generateSalt_generates16BytesWithEntropy() {
        val salt1 = BackupKeyWrap.generateSalt()
        val salt2 = BackupKeyWrap.generateSalt()

        assertEquals(BackupFileFormat.PBKDF2_SALT_LEN, salt1.size)
        assertEquals(BackupFileFormat.PBKDF2_SALT_LEN, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun generateIv_generates12BytesWithEntropy() {
        val iv1 = BackupKeyWrap.generateIv()
        val iv2 = BackupKeyWrap.generateIv()

        assertEquals(BackupFileFormat.IV_LEN, iv1.size)
        assertEquals(BackupFileFormat.IV_LEN, iv2.size)
        assertFalse(iv1.contentEquals(iv2))
    }

    @Test
    fun deriveKek_enforcesSaltLengthRequirement() {
        val shortSalt = ByteArray(15) { 1 }
        val longSalt = ByteArray(17) { 1 }
        val emptySalt = ByteArray(0)

        assertThrows(IllegalArgumentException::class.java) {
            BackupKeyWrap.deriveKek("password".toCharArray(), shortSalt, iterations = 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupKeyWrap.deriveKek("password".toCharArray(), longSalt, iterations = 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupKeyWrap.deriveKek("password".toCharArray(), emptySalt, iterations = 1_000)
        }
    }

    @Test
    fun deriveKek_isDeterministicForSamePassphraseAndSalt() {
        val salt = BackupKeyWrap.generateSalt()
        val pass = "deterministic_test_passphrase".toCharArray()

        val kek1 = BackupKeyWrap.deriveKek(pass, salt, iterations = 1_000)
        val kek2 = BackupKeyWrap.deriveKek(pass, salt, iterations = 1_000)

        assertArrayEquals(kek1.encoded, kek2.encoded)
    }

    @Test
    fun deriveKek_producesDifferentKeysForDifferentPassphrases() {
        val salt = BackupKeyWrap.generateSalt()
        val kek1 = BackupKeyWrap.deriveKek("passA".toCharArray(), salt, iterations = 1_000)
        val kek2 = BackupKeyWrap.deriveKek("passB".toCharArray(), salt, iterations = 1_000)

        assertFalse(kek1.encoded.contentEquals(kek2.encoded))
    }

    @Test
    fun deriveKek_producesDifferentKeysForDifferentSalts() {
        val pass = "same_pass".toCharArray()
        val salt1 = BackupKeyWrap.generateSalt()
        val salt2 = BackupKeyWrap.generateSalt()

        val kek1 = BackupKeyWrap.deriveKek(pass, salt1, iterations = 1_000)
        val kek2 = BackupKeyWrap.deriveKek(pass, salt2, iterations = 1_000)

        assertFalse(kek1.encoded.contentEquals(kek2.encoded))
    }

    @Test
    fun wrapDek_producesUniqueIvAndCiphertextEachTime() {
        val salt = BackupKeyWrap.generateSalt()
        val kek = BackupKeyWrap.deriveKek("pass".toCharArray(), salt, iterations = 1_000)
        val dek = SecretKeySpec(ByteArray(32) { 5 }, "AES")

        val (iv1, wrapped1) = BackupKeyWrap.wrapDek(dek, kek)
        val (iv2, wrapped2) = BackupKeyWrap.wrapDek(dek, kek)

        assertFalse(iv1.contentEquals(iv2))
        assertFalse(wrapped1.contentEquals(wrapped2))
        // 32 bytes key + 16 bytes auth tag = 48 bytes
        assertEquals(48, wrapped1.size)
        assertEquals(48, wrapped2.size)
    }

    @Test
    fun unwrapDek_withTamperedCiphertext_throwsException() {
        val salt = BackupKeyWrap.generateSalt()
        val kek = BackupKeyWrap.deriveKek("pass".toCharArray(), salt, iterations = 1_000)
        val dek = SecretKeySpec(ByteArray(32) { 3 }, "AES")

        val (iv, wrapped) = BackupKeyWrap.wrapDek(dek, kek)
        val tamperedWrapped = wrapped.copyOf()
        tamperedWrapped[0] = (tamperedWrapped[0].toInt() xor 0x01).toByte()

        assertThrows(Exception::class.java) {
            BackupKeyWrap.unwrapDek(tamperedWrapped, iv, kek)
        }
    }

    @Test
    fun unwrapDek_withTamperedIv_throwsException() {
        val salt = BackupKeyWrap.generateSalt()
        val kek = BackupKeyWrap.deriveKek("pass".toCharArray(), salt, iterations = 1_000)
        val dek = SecretKeySpec(ByteArray(32) { 4 }, "AES")

        val (iv, wrapped) = BackupKeyWrap.wrapDek(dek, kek)
        val tamperedIv = iv.copyOf()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()

        assertThrows(Exception::class.java) {
            BackupKeyWrap.unwrapDek(wrapped, tamperedIv, kek)
        }
    }
}
