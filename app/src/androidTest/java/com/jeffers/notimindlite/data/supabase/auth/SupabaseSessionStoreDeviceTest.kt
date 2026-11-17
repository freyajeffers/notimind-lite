package com.jeffers.notimindlite.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private val isolatedPreferenceKeys = listOf(
        "secret_access_token",
        "secret_refresh_token",
        "access_token",
        "refresh_token"
    )
    private var originalPreferenceValues: Map<String, String?> = emptyMap()

    @Before
    fun isolateSessionPreferences() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        originalPreferenceValues = isolatedPreferenceKeys.associateWith { prefs.getString(it, null) }
        prefs
            .edit()
            .also { editor ->
                isolatedPreferenceKeys.forEach(editor::remove)
            }.commit()
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
    }

    @Test
    fun saveAndReadAccessToken_encryptsAndDecryptsSuccessfully() {
        val store = SupabaseSessionStore(context)
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample_payload.sample_signature"

        store.saveAccessToken(token)

        val retrieved = store.readAccessToken()
        assertEquals(token, retrieved)

        // Verify stored value in SharedPreferences is encrypted (not plaintext)
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val encryptedValue = prefs.getString("secret_access_token", null)
        assertNotNull(encryptedValue)
        assertFalse(encryptedValue!!.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    @Test
    fun readAccessToken_migratesPlaintextTokenToEncryptedStorage() {
        val token = "legacy-access-token"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("access_token", token).commit())

        val store = SupabaseSessionStore(context)

        assertEquals(token, store.readAccessToken())
        assertNull(prefs.getString("access_token", null))
        val encryptedValue = prefs.getString("secret_access_token", null)
        assertNotNull(encryptedValue)
        assertFalse(encryptedValue!!.contains(token))
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
