package com.jeffers.notimindlite.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    @Test
    fun defaultConstructor_hasAllNullFields() {
        val filter = NotificationFilter()

        assertNull(filter.query)
        assertNull(filter.packageNames)
        assertNull(filter.channelId)
        assertNull(filter.minImportance)
        assertNull(filter.startTimeMs)
        assertNull(filter.endTimeMs)
        assertNull(filter.isClearable)
        assertNull(filter.isDismissed)
        assertNull(filter.isPinned)
        assertNull(filter.dismissReason)
    }

    @Test
    fun customConstructor_preservesAllValues() {
        val packages = listOf("com.example.one", "com.example.two")
        val filter = NotificationFilter(
            query = "urgent",
            packageNames = packages,
            channelId = "alerts",
            minImportance = 3,
            startTimeMs = 1000L,
            endTimeMs = 5000L,
            isClearable = true,
            isDismissed = false,
            isPinned = true,
            dismissReason = 1
        )

        assertEquals("urgent", filter.query)
        assertEquals(packages, filter.packageNames)
        assertEquals("alerts", filter.channelId)
        assertEquals(3, filter.minImportance)
        assertEquals(1000L, filter.startTimeMs)
        assertEquals(5000L, filter.endTimeMs)
        assertEquals(true, filter.isClearable)
        assertEquals(false, filter.isDismissed)
        assertEquals(true, filter.isPinned)
        assertEquals(1, filter.dismissReason)
    }

    @Test
    fun copy_allowsSelectiveModification() {
        val initial = NotificationFilter(query = "search", channelId = "ch1")
        val modified = initial.copy(query = "updated", isPinned = true)

        assertEquals("updated", modified.query)
        assertEquals("ch1", modified.channelId)
        assertTrue(modified.isPinned == true)
        assertNull(initial.isPinned)
    }

    @Test
    fun equalityAndHashCode_conformToDataClassContract() {
        val filter1 = NotificationFilter(query = "q", packageNames = listOf("pkg"))
        val filter2 = NotificationFilter(query = "q", packageNames = listOf("pkg"))
        val different = NotificationFilter(query = "other", packageNames = listOf("pkg"))

        assertEquals(filter1, filter2)
        assertEquals(filter1.hashCode(), filter2.hashCode())
        assertNotEquals(filter1, different)
    }
}
