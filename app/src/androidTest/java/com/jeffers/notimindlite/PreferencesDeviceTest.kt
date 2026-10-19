package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesDeviceTest {
    private lateinit var context: Context
    private lateinit var repository: PreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = PreferencesRepository(context)
    }

    @Test
    fun displayManager_updatesAndEmitsThemeAccent() = runBlocking {
        val initial = repository.displayManager.themeAccent.first()
        assertNotNull(initial)

        repository.displayManager.setThemeAccent("amoled")
        val updated = repository.displayManager.themeAccent.first()
        assertEquals("amoled", updated)

        // Restore default
        repository.displayManager.setThemeAccent("system")
        assertEquals("system", repository.displayManager.themeAccent.first())
    }

    @Test
    fun securityManager_togglesPiiRedaction() {
        repository.securityManager.setRedactPii(true)
        assertTrue(repository.securityManager.redactPii.value)

        repository.securityManager.setRedactPii(false)
        assertFalse(repository.securityManager.redactPii.value)
    }

    @Test
    fun exportManager_togglesAnonymize() {
        repository.exportManager.setAnonymize(true)
        assertTrue(repository.exportManager.anonymize.value)

        repository.exportManager.setAnonymize(false)
        assertFalse(repository.exportManager.anonymize.value)
    }

    @Test
    fun profileManager_defaultProfileIsActive() {
        val activeId = repository.profileManager.activeProfileId.value
        assertEquals(PreferencesRepository.DEFAULT_PROFILE_ID, activeId)
        val profiles = repository.profileManager.profiles.value
        assertTrue(profiles.any { it.id == PreferencesRepository.DEFAULT_PROFILE_ID })
    }

    @Test
    fun captureManager_handlesAllowlistAndBlocklist() {
        repository.captureManager.setAllowlist("com.trusted.app")
        val allowlist = repository.captureManager.allowlist.value
        assertTrue(allowlist.contains("com.trusted.app"))

        repository.captureManager.setBlocklist("com.spam.app")
        val blocklist = repository.captureManager.blocklist.value
        assertTrue(blocklist.contains("com.spam.app"))
    }
}
