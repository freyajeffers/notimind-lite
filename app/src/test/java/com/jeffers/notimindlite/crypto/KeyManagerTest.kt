package com.jeffers.notimindlite.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyManagerTest {

    @Test
    fun `getOrCreateMasterKeyAlias returns valid alias string`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = KeyManager.getOrCreateMasterKeyAlias(context, "custom_alias_key")
        assertNotNull(alias)
        assertEquals("custom_alias_key", alias)
    }

    @Test
    fun `getOrCreateMasterKeyAlias returns fallback default when alias is null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = KeyManager.getOrCreateMasterKeyAlias(context, null)
        assertNotNull(alias)
        assertEquals("notimind_master_key", alias)
    }
}
