package com.jeffers.notimindlite

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.DatabaseExporter
import com.jeffers.notimindlite.util.ExportRangePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ExportRangePresetTest {
    private val now = 1_700_000_000_000L

    @Test
    fun allTime_hasNoBounds() {
        val selection = ExportRangePreset.ALL_TIME.toSelection(now)

        assertNull(selection.fromInclusiveMillis)
        assertNull(selection.toExclusiveMillis)
        assertTrue(selection.packageNames.isEmpty())
    }

    @Test
    fun last24Hours_producesHalfOpenDayWindow() {
        val selection = ExportRangePreset.LAST_24_HOURS.toSelection(now)

        assertEquals(now - TimeUnit.HOURS.toMillis(24), selection.fromInclusiveMillis)
        assertEquals(now + 1, selection.toExclusiveMillis)
    }

    @Test
    fun last7Days_andLast30Days_scaleCorrectly() {
        val week = ExportRangePreset.LAST_7_DAYS.toSelection(now)
        val month = ExportRangePreset.LAST_30_DAYS.toSelection(now)

        assertEquals(now - TimeUnit.DAYS.toMillis(7), week.fromInclusiveMillis)
        assertEquals(now - TimeUnit.DAYS.toMillis(30), month.fromInclusiveMillis)
    }

    @Test
    fun presets_composeWithSelectionFiltering() {
        val notifications = listOf(
            notification("recent", postTime = now - TimeUnit.HOURS.toMillis(1)),
            notification("two-days-old", postTime = now - TimeUnit.DAYS.toMillis(2)),
            notification("forty-days-old", postTime = now - TimeUnit.DAYS.toMillis(40)),
        )

        val last24 = DatabaseExporter.selectForExport(
            notifications,
            ExportRangePreset.LAST_24_HOURS.toSelection(now)
        )
        val last7 = DatabaseExporter.selectForExport(
            notifications,
            ExportRangePreset.LAST_7_DAYS.toSelection(now)
        )
        val allTime = DatabaseExporter.selectForExport(
            notifications,
            ExportRangePreset.ALL_TIME.toSelection(now)
        )

        assertEquals(listOf("recent"), last24.map { it.key })
        assertEquals(listOf("recent", "two-days-old"), last7.map { it.key })
        assertEquals(3, allTime.size)
    }

    private fun notification(key: String, postTime: Long) = NotificationEntity(
        key = key,
        packageName = "com.example.app",
        appName = "Example",
        title = "Title",
        content = "Content",
        postTime = postTime,
    )
}
