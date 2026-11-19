package com.jeffers.notimindlite.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSelectorUtilsTest {
    @Test
    fun installedObservedAppsKeepsInstalledAndHidesUninstalledPackages() {
        val observed = listOf(
            "com.example.installed" to "Installed App",
            "com.example.uninstalled" to "Old App"
        )

        assertEquals(
            listOf("com.example.installed" to "Installed App"),
            filterInstalledObservedApps(observed, setOf("com.example.installed"))
        )
    }
}
