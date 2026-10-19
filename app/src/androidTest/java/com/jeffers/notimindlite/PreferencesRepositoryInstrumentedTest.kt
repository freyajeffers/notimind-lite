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

    private fun preferencesManager(repo: PreferencesRepository): Any = repo.securityManager
}
