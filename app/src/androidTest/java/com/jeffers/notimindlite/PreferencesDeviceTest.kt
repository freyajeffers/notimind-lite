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

    @Test
    fun storageManager_updatesDbAndCacheLimits() {
        repository.storageManager.setMaxDbMb(100)
        assertEquals(100, repository.storageManager.maxDbMb.value)

        repository.storageManager.setVectorCacheMax(500)
        assertEquals(500, repository.storageManager.vectorCacheMax.value)

        repository.storageManager.setLowMemoryMode(true)
        assertTrue(repository.storageManager.lowMemoryMode.value)

        repository.storageManager.setLowMemoryMode(false)
        assertFalse(repository.storageManager.lowMemoryMode.value)
    }

    @Test
    fun retentionDays_clampsBoundsProperly() {
        repository.setRetentionDays(50)
        assertEquals(50, repository.retentionDays.value)

        repository.setRetentionDays(0)
        assertEquals(1, repository.retentionDays.value)

        repository.setRetentionDays(10000)
        assertEquals(3650, repository.retentionDays.value)
    }

    @Test
    fun semanticWeight_clampsBoundsProperly() {
        repository.setSemanticWeight(75)
        assertEquals(75, repository.semanticWeight.value)

        repository.setSemanticWeight(-10)
        assertEquals(0, repository.semanticWeight.value)

        repository.setSemanticWeight(150)
        assertEquals(100, repository.semanticWeight.value)
    }

    @Test
    fun automationManager_persistsConfiguredActions() {
        repository.automationManager.setAutoActOnNotification(true)
        assertTrue(repository.automationManager.autoActOnNotification.value)

        repository.automationManager.setDefaultReplyMethod("copy")
        assertEquals("copy", repository.automationManager.defaultReplyMethod.value)

        repository.automationManager.setLongPressAction("reply")
        assertEquals("reply", repository.automationManager.longPressAction.value)
    }
}
