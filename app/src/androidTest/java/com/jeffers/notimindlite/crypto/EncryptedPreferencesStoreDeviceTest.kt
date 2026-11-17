package com.jeffers.notimindlite.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesStoreDeviceTest {
    @Test
    fun rotatesMasterKeyWithoutLosingAuthenticatedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase2_rotation_${System.nanoTime()}"
        val initial = EncryptedPreferencesStore.open(context, name)
        check(initial.edit().putString("token", "device-bound-secret").commit())

        val rotated = EncryptedPreferencesStore.rotate(context, name)

        assertEquals("device-bound-secret", rotated.getString("token", null))
        assertEquals("device-bound-secret", EncryptedPreferencesStore.open(context, name).getString("token", null))
        rotated.edit().clear().commit()
    }
}
