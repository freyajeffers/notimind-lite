package com.jeffers.notimindlite.data

import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferencesRepositoryProfilesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun clear() {
        context.getSharedPreferences("notimind_profiles", 0).edit().clear().commit()
        context.getSharedPreferences("notimind_lite_prefs_default", 0).edit().clear().commit()
    }

    @Test fun profileSettingsAndExportAreIsolated() {
        val repository = PreferencesRepository(context)
        val profile = repository.createProfile("Work")
        assertTrue(repository.setActiveProfile(profile.id))
        repository.setEnableSync(false)
        assertEquals(false, repository.enableSync.value)
        assertTrue(repository.setActiveProfile(PreferencesRepository.DEFAULT_PROFILE_ID))
        assertEquals(true, repository.enableSync.value)
        val imported = repository.importProfile(repository.exportProfile(profile.id))
        assertTrue(imported.id != profile.id)
    }
}