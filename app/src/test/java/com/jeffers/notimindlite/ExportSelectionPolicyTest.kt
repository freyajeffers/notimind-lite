package com.jeffers.notimindlite

import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.ExportRangePreset
import com.jeffers.notimindlite.util.ExportSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSelectionPolicyTest {
    private val now = 1_700_000_000_000L

    @Test
    fun appsAvailableAndNoneSelected_exportsNothing() {
        val selection = ExportSelectionPolicy.selection(
            availablePackageCount = 7,
            selectedPackages = emptySet(),
            preset = ExportRangePreset.ALL_TIME,
            nowMillis = now,
        )

        assertNull(selection)
    }

    @Test
    fun appsAvailableAndSomeSelected_filtersToThoseApps() {
        val selection = ExportSelectionPolicy.selection(
            availablePackageCount = 7,
            selectedPackages = setOf("com.example.one"),
            preset = ExportRangePreset.ALL_TIME,
            nowMillis = now,
        )

        assertNotNull(selection)
        assertEquals(setOf("com.example.one"), selection!!.packageNames)
    }

    @Test
    fun noAppsAvailable_keepsUnfilteredSelection() {
        val selection = ExportSelectionPolicy.selection(
            availablePackageCount = 0,
            selectedPackages = emptySet(),
            preset = ExportRangePreset.ALL_TIME,
            nowMillis = now,
        )

        assertNotNull(selection)
        assertTrue(selection!!.packageNames.isEmpty())
    }

    @Test
    fun clearedSelection_isNotTreatedAsEveryApp() {
        val available = 7
        val cleared = ExportSelectionPolicy.selection(
            availablePackageCount = available,
            selectedPackages = emptySet(),
            preset = ExportRangePreset.LAST_7_DAYS,
            nowMillis = now,
        )
        // The dangerous default: an empty set flowing into ExportSelection means "all".
        val direct = DatabaseExporter.ExportSelection(packageNames = emptySet())

        assertNull(cleared)
        assertTrue(direct.packageNames.isEmpty())
    }
}
