package com.jeffers.notimindlite.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
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
}
