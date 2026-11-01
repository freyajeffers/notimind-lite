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

        assertEquals(listOf(pinned.key), sectionItems(NotificationSection.PINNED, inputs).map { it.key })
        assertEquals(listOf(regular.key), sectionItems(NotificationSection.ACTIVE, inputs).map { it.key })
        assertEquals(listOf(system.key), sectionItems(NotificationSection.FILTERED, inputs).map { it.key })
        assertEquals(
            listOf(dismissed.key, dismissedPinned.key),
            sectionItems(NotificationSection.DISMISSED, inputs).map { it.key }
        )
        assertEquals(listOf(lost.key), sectionItems(NotificationSection.LOST, inputs).map { it.key })

        val restored = dismissed.copy(isDismissed = false, dismissReason = null, dismissTime = null)
        val afterRestore = inputs.copy(
            active = inputs.active + restored,
            dismissed = inputs.dismissed - dismissed
        )
        assertEquals(
            listOf(regular.key, restored.key),
            sectionItems(NotificationSection.ACTIVE, afterRestore).map { it.key }
        )
        assertEquals(
            listOf(dismissedPinned.key),
            sectionItems(NotificationSection.DISMISSED, afterRestore).map { it.key }
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
            sectionItems(NotificationSection.ACTIVE, afterSystemRestore).any { it.key == restoredSystem.key }
        )
        assertFalse(
            sectionItems(NotificationSection.FILTERED, afterSystemRestore).any { it.key == restoredSystem.key }
        )
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
