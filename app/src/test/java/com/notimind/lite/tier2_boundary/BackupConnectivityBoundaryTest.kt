package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.util.DatabaseExporter
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupConnectivityBoundaryTest : BaseRobolectricTest() {

    private val testKey = SecretKeySpec("1234567890123456".toByteArray(), "AES")

    @After
    override fun teardown() {
        unmockkAll()
        super.teardown()
    }

    @Test
    fun performEncryptedBackup_should_fail_when_internet_is_unavailable() = runTest {
        mockkObject(DatabaseExporter)
        coEvery { DatabaseExporter.performEncryptedBackup(context, testKey) } returns Result.failure(
            IllegalStateException("Active internet connection is required to create a backup")
        )

        val result = DatabaseExporter.performEncryptedBackup(context, testKey)

        assertTrue("Backup should return failure when offline", result.isFailure)
        assertEquals("Active internet connection is required to create a backup", result.exceptionOrNull()?.message)
    }

    @Test
    fun performEncryptedBackup_should_bypass_connectivity_guard_when_internet_is_available() = runTest {
        mockkObject(DatabaseExporter)
        coEvery { DatabaseExporter.performEncryptedBackup(context, testKey) } returns Result.success(java.io.File("/dev/null"))

        val result = DatabaseExporter.performEncryptedBackup(context, testKey)

        assertTrue("Backup should succeed when online", result.isSuccess)
    }
}
