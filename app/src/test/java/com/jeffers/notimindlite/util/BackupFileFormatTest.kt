package com.jeffers.notimindlite.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class BackupFileFormatTest {
    @Test
    fun writeWrappedAndParse_roundTripPreservesAllFields() {
        val salt = ByteArray(BackupFileFormat.PBKDF2_SALT_LEN) { (it + 1).toByte() }
        val kekIv = ByteArray(BackupFileFormat.IV_LEN) { (it + 10).toByte() }
        val wrappedDek = ByteArray(48) { (it * 2).toByte() }
        val dataIv = ByteArray(BackupFileFormat.IV_LEN) { (it + 20).toByte() }
        // 32 bytes ciphertext (> 16 bytes GCM auth tag)
        val ciphertext = ByteArray(32) { (it + 5).toByte() }

        val payload = BackupFileFormat.WrappedPayload(
            salt = salt,
            iterations = BackupFileFormat.PBKDF2_ITERATIONS,
            kekIv = kekIv,
            wrappedDek = wrappedDek,
            dataIv = dataIv,
            ciphertext = ciphertext
        )

        val encoded = BackupFileFormat.writeWrapped(payload)
        val parsed = BackupFileFormat.parse(encoded)

        assertEquals(BackupFileFormat.CURRENT_VERSION, parsed.version)
        assertEquals(BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED, parsed.keySource)
        assertEquals(BackupFileFormat.PBKDF2_ITERATIONS, parsed.pbkdf2Iterations)
        assertArrayEquals(salt, parsed.pbkdf2Salt)
        assertArrayEquals(kekIv, parsed.kekIv)
        assertArrayEquals(wrappedDek, parsed.wrappedDek)
        assertArrayEquals(dataIv, parsed.dataIv)
        assertArrayEquals(ciphertext, parsed.ciphertext)
    }

    @Test
    fun parse_fileTooShort_throwsFormatException() {
        val shortBytes = ByteArray(10) { 0 }
        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(shortBytes)
        }
        assertNotNull(exception.message)
    }

    @Test
    fun parse_invalidMagic_throwsFormatException() {
        val badMagic = ByteArrayOutputStream()
            .apply {
                write("BAD!".toByteArray(Charsets.US_ASCII))
                write(1) // version
                write(1) // keySource
                write(ByteArray(20))
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(badMagic)
        }
        assertEquals("Not a NotiMind backup file", exception.message)
    }

    @Test
    fun parse_unsupportedVersion_throwsFormatException() {
        val badVersion = ByteArrayOutputStream()
            .apply {
                write(BackupFileFormat.MAGIC)
                write(99) // future version
                write(BackupFileFormat.KEY_SOURCE_KEYSTORE_ONLY.toInt())
                write(ByteArray(BackupFileFormat.IV_LEN))
                write(ByteArray(20))
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(badVersion)
        }
        assertEquals("Unsupported backup version: 99", exception.message)
    }

    @Test
    fun parse_unknownKeySource_throwsFormatException() {
        val badKeySource = ByteArrayOutputStream()
            .apply {
                write(BackupFileFormat.MAGIC)
                write(BackupFileFormat.CURRENT_VERSION.toInt())
                write(0x7F) // unknown key source
                write(ByteArray(BackupFileFormat.IV_LEN))
                write(ByteArray(20))
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(badKeySource)
        }
        assertEquals("Unknown key source: 127", exception.message)
    }

    @Test
    fun parse_implausibleIterations_throwsFormatException() {
        val lowIterations = ByteArrayOutputStream()
            .apply {
                val dos = DataOutputStream(this)
                dos.write(BackupFileFormat.MAGIC)
                dos.writeByte(BackupFileFormat.CURRENT_VERSION.toInt())
                dos.writeByte(BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED.toInt())
                dos.write(ByteArray(BackupFileFormat.PBKDF2_SALT_LEN))
                dos.writeInt(10) // below 1000 MIN_PLAUSIBLE_ITERATIONS
                dos.write(ByteArray(BackupFileFormat.IV_LEN))
                dos.writeInt(32)
                dos.write(ByteArray(32))
                dos.write(ByteArray(BackupFileFormat.IV_LEN))
                dos.write(ByteArray(20))
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(lowIterations)
        }
        assertEquals("Implausible iteration count: 10", exception.message)
    }

    @Test
    fun parse_implausibleDekLength_throwsFormatException() {
        val zeroDekLen = ByteArrayOutputStream()
            .apply {
                val dos = DataOutputStream(this)
                dos.write(BackupFileFormat.MAGIC)
                dos.writeByte(BackupFileFormat.CURRENT_VERSION.toInt())
                dos.writeByte(BackupFileFormat.KEY_SOURCE_PASSPHRASE_WRAPPED.toInt())
                dos.write(ByteArray(BackupFileFormat.PBKDF2_SALT_LEN))
                dos.writeInt(BackupFileFormat.PBKDF2_ITERATIONS)
                dos.write(ByteArray(BackupFileFormat.IV_LEN))
                dos.writeInt(0) // <= 0 length
                dos.write(ByteArray(BackupFileFormat.IV_LEN))
                dos.write(ByteArray(20))
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(zeroDekLen)
        }
        assertEquals("Implausible DEK length: 0", exception.message)
    }

    @Test
    fun parse_truncatedCiphertext_throwsFormatException() {
        val truncatedCiphertext = ByteArrayOutputStream()
            .apply {
                val dos = DataOutputStream(this)
                dos.write(BackupFileFormat.MAGIC)
                dos.writeByte(BackupFileFormat.CURRENT_VERSION.toInt())
                dos.writeByte(BackupFileFormat.KEY_SOURCE_KEYSTORE_ONLY.toInt())
                dos.write(ByteArray(BackupFileFormat.IV_LEN))
                dos.write(ByteArray(8)) // <= 16 bytes GCM auth tag
            }.toByteArray()

        val exception = assertThrows(BackupFileFormat.FormatException::class.java) {
            BackupFileFormat.parse(truncatedCiphertext)
        }
        assertEquals("Ciphertext payload missing or truncated", exception.message)
    }
}
