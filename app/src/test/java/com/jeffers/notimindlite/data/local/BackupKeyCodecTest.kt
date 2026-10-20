package com.jeffers.notimindlite.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupKeyCodecTest {
    @Test
    fun encodeAndDecode_roundTrips256BitKeySuccessfully() {
        val rawKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val originalKey = SecretKeySpec(rawKeyBytes, "AES")

        val encoded = BackupKeyCodec.encode(originalKey)
        assertNotNull(encoded)

        val decoded = BackupKeyCodec.decode(encoded)
        assertEquals("AES", decoded.algorithm)
        assertEquals("RAW", decoded.format)
        assertArrayEquals(originalKey.encoded, decoded.encoded)
    }

    @Test
    fun encodeAndDecode_roundTrips128BitKeySuccessfully() {
        val rawKeyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val originalKey = SecretKeySpec(rawKeyBytes, "AES")

        val encoded = BackupKeyCodec.encode(originalKey)
        val decoded = BackupKeyCodec.decode(encoded)

        assertEquals("AES", decoded.algorithm)
        assertArrayEquals(originalKey.encoded, decoded.encoded)
    }

    @Test
    fun decode_withKnownBase64String_producesExpectedKeyBytes() {
        // 16 zero bytes encoded as Base64 is "AAAAAAAAAAAAAAAAAAAAAA=="
        val base64Key = "AAAAAAAAAAAAAAAAAAAAAA=="
        val expectedBytes = ByteArray(16) { 0 }

        val decodedKey = BackupKeyCodec.decode(base64Key)
        assertEquals("AES", decodedKey.algorithm)
        assertArrayEquals(expectedBytes, decodedKey.encoded)
    }

    @Test
    fun decode_withInvalidBase64String_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupKeyCodec.decode("!!!not_base64!!!")
        }
    }
}
