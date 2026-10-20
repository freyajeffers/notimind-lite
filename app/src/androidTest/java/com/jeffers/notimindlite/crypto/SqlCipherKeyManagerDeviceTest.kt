package com.jeffers.notimindlite.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Acceptance tests for SqlCipherKeyManager on physical device hardware keystore. */
@RunWith(AndroidJUnit4::class)
class SqlCipherKeyManagerDeviceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val testDb1 = "device_test_db_1"
    private val testDb2 = "device_test_db_2"
    private val testDbNoKeystore = "device_test_db_no_keystore"

    @Before
    @After
    fun cleanUp() {
        cleanDbKeys(testDb1)
        cleanDbKeys(testDb2)
        cleanDbKeys(testDbNoKeystore)
    }

    private fun cleanDbKeys(databaseName: String) {
        val prefs = context.getSharedPreferences(
            "notimind_sqlcipher_key_$databaseName",
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "notimind_sqlcipher_$databaseName"
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (_: Exception) {
            // KeyStore alias might not exist or failed to query
        }
    }

    @Test
    fun getOrCreatePassphrase_withKeystore_generates32ByteKeyAndIsIdempotent() {
        val passphrase1 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDb1,
            useKeystore = true
        )
        assertNotNull(passphrase1)
        assertEquals(32, passphrase1.size)

        // Second call should return the identical decrypted passphrase
        val passphrase2 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDb1,
            useKeystore = true
        )
        assertArrayEquals(passphrase1, passphrase2)
    }

    @Test
    fun getOrCreatePassphrase_withKeystore_isolatesKeysBetweenDatabases() {
        val passphraseDb1 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDb1,
            useKeystore = true
        )
        val passphraseDb2 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDb2,
            useKeystore = true
        )

        assertEquals(32, passphraseDb1.size)
        assertEquals(32, passphraseDb2.size)
        assertFalse(passphraseDb1.contentEquals(passphraseDb2))
    }

    @Test
    fun getOrCreatePassphrase_withoutKeystore_generates32ByteKeyAndIsIdempotent() {
        val passphrase1 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDbNoKeystore,
            useKeystore = false
        )
        assertNotNull(passphrase1)
        assertEquals(32, passphrase1.size)

        val passphrase2 = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDbNoKeystore,
            useKeystore = false
        )
        assertArrayEquals(passphrase1, passphrase2)
    }

    @Test
    fun getOrCreatePassphrase_withKeystore_storesEncryptedPayloadInSharedPreferences() {
        val passphrase = SqlCipherKeyManager.getOrCreatePassphrase(
            context,
            testDb1,
            useKeystore = true
        )
        val prefs = context.getSharedPreferences(
            "notimind_sqlcipher_key_$testDb1",
            Context.MODE_PRIVATE
        )
        val storedString = prefs.getString("encrypted_passphrase", null)
        assertNotNull(storedString)
        assertTrue(storedString!!.isNotEmpty())

        val decoded = android.util.Base64.decode(storedString, android.util.Base64.NO_WRAP)
        // Ciphertext payload contains 12-byte IV + 32-byte ciphertext + 16-byte GCM tag = 60 bytes
        assertTrue(decoded.size > 32)
        assertFalse(decoded.contentEquals(passphrase))
    }
}
