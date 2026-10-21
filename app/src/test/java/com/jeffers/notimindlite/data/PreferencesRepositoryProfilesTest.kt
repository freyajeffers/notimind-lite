package com.jeffers.notimindlite.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferencesRepository
import com.jeffers.notimindlite.data.local.ProfilePreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferencesRepositoryProfilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context
            .getSharedPreferences("notimind_profiles", 0)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("notimind_lite_prefs_default", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun profileSettingsAndExportAreIsolated() {
        val repository = PreferencesRepository(context)
        val profiles = repository.profileManager
        val profile = profiles.createProfile("Work")
        assertTrue(profiles.setActiveProfile(profile.id))
        repository.syncManager.setEnableSync(false)
        assertEquals(false, repository.syncManager.enableSync.value)
        assertTrue(profiles.setActiveProfile(ProfilePreferencesManager.DEFAULT_PROFILE_ID))
        assertEquals(true, repository.syncManager.enableSync.value)
        val imported = profiles.importProfile(profiles.exportProfile(profile.id))
        assertTrue(imported.id != profile.id)
    }

    @Test
    fun createProfile_nameTrimmingAndBlankFallback() {
        val manager = ProfilePreferencesManager(context)

        // Trimmed name
        val p1 = manager.createProfile("   Work Mode   ")
        assertEquals("Work Mode", p1.name)

        // Blank name fallback to "Profile N"
        // Initially default profile is 1, p1 is 2, so next is 3
        val p2 = manager.createProfile("   ")
        assertEquals("Profile 3", p2.name)

        // Length truncation to MAX_PROFILE_NAME_LENGTH (80)
        val longName = "A".repeat(120)
        val p3 = manager.createProfile(longName)
        assertEquals(80, p3.name.length)
        assertEquals("A".repeat(80), p3.name)
    }

    @Test
    fun deleteProfile_guardsAgainstDefaultAndActiveProfile() {
        val manager = ProfilePreferencesManager(context)
        val p1 = manager.createProfile("Personal")
        val p2 = manager.createProfile("Work")

        // Cannot delete default profile
        assertFalse(manager.deleteProfile(ProfilePreferencesManager.DEFAULT_PROFILE_ID))

        // Set p1 as active
        assertTrue(manager.setActiveProfile(p1.id))
        assertEquals(p1.id, manager.activeProfileId.value)

        // Cannot delete active profile
        assertFalse(manager.deleteProfile(p1.id))

        // Can delete non-active profile p2
        assertTrue(manager.deleteProfile(p2.id))
        assertFalse(manager.profiles.value.any { it.id == p2.id })

        // Deleting non-existent profile returns false
        assertFalse(manager.deleteProfile("non_existent_profile_id"))
    }

    @Test
    fun setActiveProfile_invalidProfileFailsAndCallbackInvokedOnSuccess() {
        val manager = ProfilePreferencesManager(context)
        val p1 = manager.createProfile("Gaming")

        var callbackCount = 0
        manager.onProfileChanged = {
            callbackCount++
        }

        // Setting nonexistent profile returns false and doesn't trigger callback
        assertFalse(manager.setActiveProfile("fake_profile_id"))
        assertEquals(0, callbackCount)
        assertEquals(ProfilePreferencesManager.DEFAULT_PROFILE_ID, manager.activeProfileId.value)

        // Setting valid profile succeeds and triggers callback
        assertTrue(manager.setActiveProfile(p1.id))
        assertEquals(1, callbackCount)
        assertEquals(p1.id, manager.activeProfileId.value)
        assertEquals("notimind_lite_prefs_${p1.id}", manager.activePreferencesName)
    }

    @Test
    fun exportProfile_throwsOnUnknownProfileAndSerializesSettings() {
        val manager = ProfilePreferencesManager(context)
        val p1 = manager.createProfile("Testing")

        // Exporting nonexistent profile throws
        assertThrows(IllegalArgumentException::class.java) {
            manager.exportProfile("invalid_id")
        }

        // Populate some SharedPreferences in p1
        val prefs = context.getSharedPreferences("notimind_lite_prefs_${p1.id}", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putBoolean("test_bool", true)
            .putInt("test_int", 42)
            .putLong("test_long", 123456789L)
            .putString("test_str", "hello")
            .commit()

        val jsonString = manager.exportProfile(p1.id)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("\"version\":1"))
        assertTrue(jsonString.contains("\"name\":\"Testing\""))
        assertTrue(jsonString.contains("\"test_bool\":true"))
        assertTrue(jsonString.contains("\"test_int\":42"))
        assertTrue(jsonString.contains("\"test_str\":\"hello\""))
    }

    @Test
    fun importProfile_rejectsInvalidVersionAndLoadsSettings() {
        val manager = ProfilePreferencesManager(context)

        // Invalid version throws
        val invalidVersionJson = """{"version": 999, "profile": {"name": "Bad"}}"""
        assertThrows(IllegalArgumentException::class.java) {
            manager.importProfile(invalidVersionJson)
        }

        // Valid payload
        val validJson =
            """
            {
                "version": 1,
                "profile": {
                    "id": "valid-profile-uuid-1234",
                    "name": "Restored Profile",
                    "createdAt": 1700000000000
                },
                "settings": {
                    "imported_flag": true,
                    "imported_count": 7
                }
            }
            """.trimIndent()

        val imported = manager.importProfile(validJson)
        assertEquals("valid-profile-uuid-1234", imported.id)
        assertEquals("Restored Profile", imported.name)
        assertEquals(1700000000000L, imported.createdAt)
        assertTrue(manager.profiles.value.any { it.id == imported.id })

        val restoredPrefs = context.getSharedPreferences(
            "notimind_lite_prefs_${imported.id}",
            Context.MODE_PRIVATE
        )
        assertTrue(restoredPrefs.getBoolean("imported_flag", false))
        assertEquals(7, restoredPrefs.getInt("imported_count", 0))
    }
}
