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
import java.security.KeyStore

/** Physical-device acceptance test for SupabaseSessionStore encrypted keystore tokens. */
@RunWith(AndroidJUnit4::class)
class SupabaseSessionStoreDeviceTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val alias = "notimind_supabase_session"
    private val prefsName = "supabase_auth"

    @Before
    @After
    fun cleanUp() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
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
