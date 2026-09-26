package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerformancePreferencesTest {
    @Test fun defaultsAreSafe() {
        val prefs = PreferencesRepository(ApplicationProvider.getApplicationContext())
        assertEquals(PreferencesRepository.DEFAULT_THREAD_POOL_SIZE, prefs.getThreadPoolSize())
        assertTrue(prefs.isEmbeddingOffloadEnabled())
    }

    @Test fun valuesAreClampedAndPersisted() {
        val prefs = PreferencesRepository(ApplicationProvider.getApplicationContext<Context>())
        prefs.setThreadPoolSize(0)
        prefs.setEmbeddingRateMs(-1)
        prefs.setDbCompactionDays(0)
        prefs.setReindexDays(0)
        assertEquals(1, prefs.getThreadPoolSize())
        assertEquals(0L, prefs.getEmbeddingRateMs())
        assertEquals(1, prefs.getDbCompactionDays())
        assertEquals(1, prefs.getReindexDays())
    }
}
