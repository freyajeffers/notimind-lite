package com.jeffers.notimindlite.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSelectorUtilsTest {
    @Test
    fun keepsUninstalledPackagesThatStillHaveRecords() {
        val observed = listOf(
            "com.example.installed" to "Installed App",
            "com.example.uninstalled" to "Old App"
        )

        assertEquals(
            listOf(
                "com.example.installed" to "Installed App",
                "com.example.uninstalled" to "Old App"
            ),
            observedAppsWithRecords(
                observed,
                setOf("com.example.installed", "com.example.uninstalled")
            )
        )
    }

    @Test
    fun hidesAppsWithNoRecords() {
        val observed = listOf(
            "com.example.withrecords" to "Has History",
            "com.example.empty" to "Never Notified"
        )

        assertEquals(
            listOf("com.example.withrecords" to "Has History"),
            observedAppsWithRecords(observed, setOf("com.example.withrecords"))
        )
    }

    @Test
    fun deduplicatesRepeatedPackages() {
        val observed = listOf(
            "com.example.app" to "Example",
            "com.example.app" to "Example"
        )

        assertEquals(
            listOf("com.example.app" to "Example"),
            observedAppsWithRecords(observed, setOf("com.example.app"))
        )
    }
}
