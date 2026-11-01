package com.jeffers.notimindlite.domain.clustering

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicClusterHelpersFailureTest {
    @Test
    fun applicationLabel_whenPackageIsMissingLogsAndReturnsNull() {
        val packageManager = mockk<PackageManager>()
        val app = ApplicationInfo().apply { packageName = "com.example.missing" }
        every { packageManager.getApplicationLabel(app) } throws PackageManager.NameNotFoundException("missing")

        mockkStatic(Log::class)
        try {
            every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
            assertNull(applicationLabel(packageManager, app))
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
