package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedSearchStateManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var profileManager: ProfilePreferencesManager
    private lateinit var stateManager: SharedSearchStateManager

    @Before
    fun setUp() {
        context
            .getSharedPreferences("notimind_profiles", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("notimind_lite_prefs_default", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        profileManager = ProfilePreferencesManager(context)
        stateManager = SharedSearchStateManager(context, profileManager)
    }

    @Test
    fun initialState_hasDefaultEmptyValues() {
        assertEquals("", stateManager.query.value)
        assertNull(stateManager.packages.value)
        assertNull(stateManager.reason.value)
        assertTrue(stateManager.recentSearches.value.isEmpty())
    }

    @Test
    fun setQuery_updatesFlowAndPersists() {
        stateManager.setQuery("test query")
        assertEquals("test query", stateManager.query.value)

        // Verify across reload
        val newManager = SharedSearchStateManager(context, profileManager)
        assertEquals("test query", newManager.query.value)
    }

    @Test
    fun setPackages_updatesFlowAndParsesCommaSeparatedList() {
        val packages = listOf("com.example.chat", "com.example.mail")
        stateManager.setPackages(packages)
        assertEquals(packages, stateManager.packages.value)

        // Reload to verify persistence
        val newManager = SharedSearchStateManager(context, profileManager)
        assertEquals(packages, newManager.packages.value)

        // Reset with null
        stateManager.setPackages(null)
        assertNull(stateManager.packages.value)
    }

    @Test
    fun setReason_updatesFlowAndHandlesNull() {
        stateManager.setReason(3)
        assertEquals(3, stateManager.reason.value)

        // Reload to verify persistence
        val newManager = SharedSearchStateManager(context, profileManager)
        assertEquals(3, newManager.reason.value)

        // Set null (cleared)
        stateManager.setReason(null)
        assertNull(stateManager.reason.value)
    }

    @Test
    fun recentSearches_deduplicatesAndCapsAtTen() {
        // Add 12 distinct items
        for (i in 1..12) {
            stateManager.addRecentSearch("search_$i")
        }

        val searches = stateManager.recentSearches.value
        assertEquals(10, searches.size)
        // Most recent should be at the front
        assertEquals("search_12", searches[0])
        assertEquals("search_11", searches[1])
        assertEquals("search_3", searches[9])

        // Adding an existing search bumps it to the top
        stateManager.addRecentSearch("search_5")
        val updated = stateManager.recentSearches.value
        assertEquals(10, updated.size)
        assertEquals("search_5", updated[0])
    }

    @Test
    fun removeRecentSearch_and_resetRecentSearches() {
        stateManager.addRecentSearch("first")
        stateManager.addRecentSearch("second")
        stateManager.addRecentSearch("third")
        assertEquals(3, stateManager.recentSearches.value.size)

        stateManager.removeRecentSearch("second")
        assertEquals(listOf("third", "first"), stateManager.recentSearches.value)

        stateManager.resetRecentSearches()
        assertTrue(stateManager.recentSearches.value.isEmpty())

        // Verify persistence of reset
        val newManager = SharedSearchStateManager(context, profileManager)
        assertTrue(newManager.recentSearches.value.isEmpty())
    }

    @Test
    fun reload_synchronizesFromDisk() {
        stateManager.setQuery("initial query")
        stateManager.setReason(5)
        stateManager.setPackages(listOf("com.pkg.app"))

        // Modify underlying SharedPreferences directly
        val prefs = context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(profileManager.activePreferencesName, Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("ui_shared_search_query", "externally modified")
            .putInt("ui_shared_selected_reason", 2)
            .putString("ui_shared_selected_packages", "com.pkg.direct1,com.pkg.direct2")
            .commit()

        stateManager.reload()

        assertEquals("externally modified", stateManager.query.value)
        assertEquals(2, stateManager.reason.value)
        assertEquals(listOf("com.pkg.direct1", "com.pkg.direct2"), stateManager.packages.value)
    }
}
