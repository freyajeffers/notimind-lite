package com.jeffers.notimindlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var preferencesRepository: PreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = PreferencesRepository(context)
    }

    @Test
    fun defaultPreferences_areLoadedCorrectly() = runBlocking {
        assertNotNull(preferencesRepository.storageManager)
        assertNotNull(preferencesManager(preferencesRepository))

        val anonymize = preferencesRepository.anonymizeTitles.value
        assertEquals(false, anonymize)

        val retentionDays = preferencesRepository.retentionDays.value
        assertTrue(retentionDays >= 0)
    }

    @Test
    fun activeProfile_returnsDefaultProfileIdInitially() {
        val activeProfile = PreferencesRepository.activeProfileId(context)
        assertEquals(PreferencesRepository.DEFAULT_PROFILE_ID, activeProfile)
    }

    @Test
    fun themeAccent_hasValidDefault() = runBlocking {
        val accent = preferencesRepository.displayManager.themeAccent.first()
        assertTrue(accent.isNotBlank())
    }

    @Test
    fun telemetryManager_updatesEnabledAndLevel() {
        preferencesRepository.telemetryManager.setEnabled(true)
        assertTrue(preferencesRepository.telemetryManager.enabled.value)

        preferencesRepository.telemetryManager.setLevel("full")
        assertEquals("full", preferencesRepository.telemetryManager.level.value)

        preferencesRepository.telemetryManager.setLevel("minimal")
        assertEquals("minimal", preferencesRepository.telemetryManager.level.value)

        preferencesRepository.telemetryManager.setEnabled(false)
        assertEquals(false, preferencesRepository.telemetryManager.enabled.value)
    }

    @Test
    fun syncManager_updatesFlagsAndSyncConstraints() {
        preferencesRepository.syncManager.setEnableSync(true)
        assertTrue(preferencesRepository.syncManager.enableSync.value)

        preferencesRepository.syncManager.setSyncWifiOnly(true)
        assertTrue(preferencesRepository.syncManager.syncWifiOnly.value)

        preferencesRepository.syncManager.setSyncChargingOnly(true)
        assertTrue(preferencesRepository.syncManager.syncChargingOnly.value)

        preferencesRepository.syncManager.setUseSupabaseSync(true)
        assertTrue(preferencesRepository.syncManager.useSupabaseSync.value)

        // Reset
        preferencesRepository.syncManager.setEnableSync(false)
        assertEquals(false, preferencesRepository.syncManager.enableSync.value)
    }

    @Test
    fun displayManager_updatesUiDisplayPreferences() {
        preferencesRepository.displayManager.setCompactMode(true)
        assertTrue(preferencesRepository.displayManager.compactMode.value)

        preferencesRepository.displayManager.setShowAppIcons(false)
        assertEquals(false, preferencesRepository.displayManager.showAppIcons.value)

        preferencesRepository.displayManager.setGroupByApp(true)
        assertTrue(preferencesRepository.displayManager.groupByApp.value)

        preferencesRepository.displayManager.setPreviewLength(200)
        assertEquals(200, preferencesRepository.displayManager.previewLength.value)
    }

    @Test
    fun preferencesRepository_topLevelFlagsRoundTrip() {
        preferencesRepository.setAutoDeleteOnRead(true)
        // In debug builds, autoDeleteOnRead is safely constrained to false to prevent accidental data loss
        assertEquals(BuildConfig.DEBUG.not(), preferencesRepository.autoDeleteOnRead.value)

        preferencesRepository.setEnableSemanticRanking(false)
        assertEquals(false, preferencesRepository.enableSemanticRanking.value)

        preferencesRepository.setExportEncryption(false)
        assertEquals(false, preferencesRepository.exportEncryption.value)

        preferencesRepository.setBackupIntervalDays(7)
        assertEquals(7, preferencesRepository.backupIntervalDays.value)

        // Restore defaults
        preferencesRepository.setAutoDeleteOnRead(false)
        preferencesRepository.setEnableSemanticRanking(true)
        preferencesRepository.setExportEncryption(true)
        preferencesRepository.setBackupIntervalDays(0)
    }

    private fun preferencesManager(repo: PreferencesRepository): Any = repo.securityManager
}
