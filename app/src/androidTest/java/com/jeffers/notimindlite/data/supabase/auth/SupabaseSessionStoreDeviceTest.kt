package com.jeffers.notimindlite.data.supabase.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.crypto.EncryptedPreferencesStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device acceptance test for SupabaseSessionStore encrypted keystore tokens. */
@RunWith(AndroidJUnit4::class)
class SupabaseSessionStoreDeviceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val prefsName = "supabase_auth"
    private val securePrefsName = "supabase_auth_secure"
    private val isolatedPreferenceKeys = listOf(
        "secret_access_token",
        "secret_refresh_token",
        "access_token",
        "refresh_token"
    )
    private var originalPreferenceValues: Map<String, String?> = emptyMap()

    /**
     * The store persists to two files: the legacy plaintext prefs and the
     * EncryptedSharedPreferences file. Clearing only the legacy file let values
     * written by one test leak into the next, because reads hit the encrypted
     * file first and skip the legacy migration path.
     */
    private fun clearSecurePreferences() {
        EncryptedPreferencesStore
            .open(context, securePrefsName)
            .edit()
            .clear()
            .commit()
    }

    @Before
    fun isolateSessionPreferences() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        originalPreferenceValues = isolatedPreferenceKeys.associateWith { prefs.getString(it, null) }
        prefs
            .edit()
            .also { editor ->
                isolatedPreferenceKeys.forEach(editor::remove)
            }.commit()
        clearSecurePreferences()
    }

    @After
    fun restoreSessionPreferences() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs
            .edit()
            .also { editor ->
                isolatedPreferenceKeys.forEach(editor::remove)
                originalPreferenceValues.forEach { (key, value) ->
                    if (value != null) editor.putString(key, value)
                }
            }.commit()
        clearSecurePreferences()
    }

    @Test
    fun saveAndReadAccessToken_encryptsAndDecryptsSuccessfully() {
        val store = SupabaseSessionStore(context)
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample_payload.sample_signature"

        store.saveAccessToken(token)

        val retrieved = store.readAccessToken()
        assertEquals(token, retrieved)

        // The token must not be recoverable as plaintext from the backing file.
        // Checking through the SharedPreferences API would always show plaintext,
        // because EncryptedSharedPreferences decrypts transparently on read.
        val sharedPrefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        val secureFiles = sharedPrefsDir
            .listFiles { file -> file.name.startsWith("$securePrefsName.v") }
            .orEmpty()
        assertTrue(secureFiles.isNotEmpty())
        assertTrue(secureFiles.none { it.readText().contains(token) })
    }

    @Test
    fun readAccessToken_migratesPlaintextTokenToEncryptedStorage() {
        val token = "legacy-access-token"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("access_token", token).commit())

        val store = SupabaseSessionStore(context)

        assertEquals(token, store.readAccessToken())
        assertNull(prefs.getString("access_token", null))
        // Migration must move the value into the encrypted store and clear the
        // plaintext columns, leaving no copy behind.
        assertNull(prefs.getString("secret_access_token", null))
        assertEquals(token, store.readAccessToken())
    }

    @Test
    fun readAccessToken_invalidEncryptedValueDoesNotFallBackToPlaintext() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertTrue(
            prefs
                .edit()
                .putString("secret_access_token", "malformed-ciphertext")
                .putString("access_token", "legacy-access-token")
                .commit()
        )

        assertNull(SupabaseSessionStore(context).readAccessToken())
        assertEquals("legacy-access-token", prefs.getString("access_token", null))
    }

    @Test
    fun readAccessToken_whenEmpty_returnsNull() {
        val store = SupabaseSessionStore(context)
        assertNull(store.readAccessToken())
    }

    @Test
    fun refreshSessionIfNeeded_withoutRefreshToken_returnsFailure() = runBlocking {
        val store = SupabaseSessionStore(context)
        val result = store.refreshSessionIfNeeded(
            supabaseUrl = "https://example.supabase.co",
            anonKey = "anon_key_123"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
