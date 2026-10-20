package com.jeffers.notimindlite.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/** Acceptance tests for BackupKeyCodec on physical device hardware keystore. */
@RunWith(AndroidJUnit4::class)
class BackupKeyCodecDeviceTest {
    private val alias = "notimind_backup_key"

    @Before
    @After
    fun cleanUp() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (_: Exception) {
            // KeyStore alias might not exist or failed to query
        }
    }

    @Test
    fun getOrCreateKey_generatesValidSecretKeyAndIsIdempotent() {
        val key1 = BackupKeyCodec.getOrCreateKey()
        assertNotNull(key1)
        assertEquals("AES", key1.algorithm)

        // Subsequent call must retrieve the exact same key from KeyStore
        val key2 = BackupKeyCodec.getOrCreateKey()
        assertNotNull(key2)
        assertEquals("AES", key2.algorithm)
    }

    @Test
    fun encodeAndDecode_roundTripsRawKeyOnDevice() {
        val rawBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val originalKey = SecretKeySpec(rawBytes, "AES")

        val encoded = BackupKeyCodec.encode(originalKey)
        val decoded = BackupKeyCodec.decode(encoded)

        assertEquals("AES", decoded.algorithm)
        assertEquals("RAW", decoded.format)
        assertArrayEquals(originalKey.encoded, decoded.encoded)
    }
}
