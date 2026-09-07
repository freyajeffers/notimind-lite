package com.notimind.lite.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.jeffers.notimindlite.util.AppInitializer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppInitializerTest {

    @Test
    fun `initialize should be idempotent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // First call should initialize.
        AppInitializer.initialize(context)

        // H3 idempotency assert: a second initialize() must NOT re-register Firebase or
        // otherwise alter global state. We compare the FirebaseApp instance set before vs
        // after the second call — a side-effect-free initialize leaves the set unchanged.
        val firebaseAppsAfterFirst = FirebaseApp.getApps(context).toList()
        AppInitializer.initialize(context)
        val firebaseAppsAfterSecond = FirebaseApp.getApps(context).toList()
        assertEquals(
            "AppInitializer.initialize must be idempotent — second call must not register Firebase again",
            firebaseAppsAfterFirst,
            firebaseAppsAfterSecond
        )
    }
}
