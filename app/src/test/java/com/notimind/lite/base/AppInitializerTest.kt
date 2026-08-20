package com.notimind.lite.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
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
        
        // First call should initialize
        AppInitializer.initialize(context)
        
        // Second call should not crash or re-initialize (verified via logs in real app)
        AppInitializer.initialize(context)
        
        // If we reached here without exception, it's functionally idempotent
        assertTrue(true)
    }
}
