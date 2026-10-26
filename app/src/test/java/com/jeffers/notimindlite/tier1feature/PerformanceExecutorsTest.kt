package com.jeffers.notimindlite.tier1feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.util.PerformanceExecutors
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerformanceExecutorsTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun resetExecutor() {
        PerformanceExecutors.shutdown()
    }

    @Test
    fun embeddingExecutor_reusesPoolUntilShutdown() {
        val first = PerformanceExecutors.embeddingExecutor(context)

        assertSame(first, PerformanceExecutors.embeddingExecutor(context))

        PerformanceExecutors.shutdown()
        val replacement = PerformanceExecutors.embeddingExecutor(context)

        assertTrue(first.isShutdown)
        assertNotSame(first, replacement)
    }
}
