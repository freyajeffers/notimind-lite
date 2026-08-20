package com.notimind.lite.tier2_boundary

import android.content.Context
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.NetworkUtils
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupConnectivityBoundaryTest : BaseRobolectricTest() {

    @Before
    fun setup() {
        mockkObject(NetworkUtils)
    }

    @Test
    fun `performEncryptedBackup should fail immediately when internet is unavailable`() = runBlocking {
        // Given: Internet is unavailable
        every { NetworkUtils.isInternetAvailable(any()) } returns false

        val secretKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        
        // When: Attempting to perform backup
        val result = DatabaseExporter.performEncryptedBackup(applicationContext, secretKey)

        // Then: Result should be failure with specific connectivity message
        assertTrue("Backup should fail when offline", result.isFailure)
        assertEquals("Active internet connection is required to create a backup", result.exceptionOrNull()?.message)
    }

    @Test
    fun `performEncryptedBackup should proceed when internet is available`() = runBlocking {
        // Given: Internet is available
        every { NetworkUtils.isInternetAvailable(any()) } returns true
        
        // We need to mock the internal EncryptedBackupManager.createAuthorizedBackup 
        // Since it's an object, we mock the object
        mockkObject(com.jeffers.notimindlite.data.local.EncryptedBackupManager)
        every { 
            com.jeffers.notimindlite.data.local.EncryptedBackupManager.createAuthorizedBackup(any(), any(), any(), any(), any()) 
        } returns true

        val secretKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        // When: Attempting to perform backup
        val result = DatabaseExporter.performEncryptedBackup(applicationContext, secretKey)

        // Then: Result should be success
        assertTrue("Backup should proceed when online", result.isSuccess)
    }
}

private fun <T> assertEquals(expected: T, actual: T, message: String) {
    if (expected != actual) {
        throw AssertionError("$message: Expected <$expected> but was <$actual>")
    }
}
