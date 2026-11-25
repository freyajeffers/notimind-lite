package com.jeffers.notimindlite.ui.screens

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSectionPartitionerTest {
    @Test
    fun notificationMovesToOnlyItsCurrentSection() {
        val regular = notification("regular")
        val pinned = notification("pinned", isPinned = true)
        val system = notification("system", packageName = "android")
        val dismissed = notification("dismissed", isDismissed = true, dismissReason = 1)
        val dismissedPinned = notification("dismissed-pinned", isDismissed = true, isPinned = true, dismissReason = 1)
        val lost = notification("lost", isDismissed = true, dismissReason = 8)

        val inputs = NotificationSectionItems(
            pinned = listOf(pinned, dismissedPinned),
            active = listOf(regular, pinned, system, dismissed, dismissedPinned),
            filtered = listOf(system, dismissed),
            dismissed = listOf(dismissed, dismissedPinned),
            lost = listOf(lost)
        )

        assertEquals(listOf(pinned.key), sections(NotificationSection.PINNED, inputs).map { it.key })
        assertEquals(listOf(regular.key), sections(NotificationSection.ACTIVE, inputs).map { it.key })
        assertEquals(listOf(system.key), sections(NotificationSection.FILTERED, inputs).map { it.key })
        assertEquals(
            listOf(dismissed.key, dismissedPinned.key),
            sections(NotificationSection.DISMISSED, inputs).map { it.key }
        )
        assertEquals(listOf(lost.key), sections(NotificationSection.LOST, inputs).map { it.key })

        val restored = dismissed.copy(isDismissed = false, dismissReason = null, dismissTime = null)
        val afterRestore = inputs.copy(
            active = inputs.active + restored,
            dismissed = inputs.dismissed - dismissed
        )
        assertEquals(
            listOf(regular.key, restored.key),
            sections(NotificationSection.ACTIVE, afterRestore).map { it.key }
        )
        assertEquals(
            listOf(dismissedPinned.key),
            sections(NotificationSection.DISMISSED, afterRestore).map { it.key }
        )

        val restoredSystem = system.copy(
            key = "restored-system",
            title = "Restored system item",
            restoreMetadataJson = """{"notimindRestoredNotificationId":1234}"""
        )
        val afterSystemRestore = inputs.copy(
            active = inputs.active + restoredSystem,
            filtered = inputs.filtered + restoredSystem
        )
        assertTrue(
            sections(NotificationSection.ACTIVE, afterSystemRestore).any { it.key == restoredSystem.key }
        )
        assertFalse(
            sections(NotificationSection.FILTERED, afterSystemRestore).any { it.key == restoredSystem.key }
        )
    }

    @Test
    fun blockingIsDrivenByTheUserBlocklist() {
        val system = notification("system", packageName = "android")
        val inputs = NotificationSectionItems(
            pinned = emptyList(),
            active = listOf(system),
            filtered = emptyList(),
            dismissed = emptyList(),
            lost = emptyList()
        )

        // Un-blocked: the package's notifications stay visible.
        assertTrue(
            sectionItems(NotificationSection.ACTIVE, inputs, emptySet()).any { it.key == system.key }
        )
        // Blocked: hidden, because the user's list is the only source of truth.
        assertFalse(
            sectionItems(NotificationSection.ACTIVE, inputs, setOf("android")).any { it.key == system.key }
        )
    }

    private fun sections(section: NotificationSection, items: NotificationSectionItems) =
        sectionItems(section, items, BLOCKED_PACKAGES)

    private companion object {
        val BLOCKED_PACKAGES = setOf("android")
    }

    private fun notification(
        key: String,
        packageName: String = "com.example.app",
        isDismissed: Boolean = false,
        isPinned: Boolean = false,
        dismissReason: Int? = null
    ) = NotificationEntity(
        key = key,
        packageName = packageName,
        appName = packageName,
        title = key,
        content = key,
        isDismissed = isDismissed,
        isPinned = isPinned,
        dismissReason = dismissReason
    )
}
