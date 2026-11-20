package com.jeffers.notimindlite.util

import java.util.concurrent.TimeUnit

/**
 * User-selectable time windows for a notification export.
 *
 * Every window is half-open `[fromInclusiveMillis, toExclusiveMillis)` so a
 * notification posted exactly on a boundary is counted once and only once.
 * [ALL_TIME] deliberately carries no bounds so the export stays unbounded in
 * both directions.
 */
enum class ExportRangePreset {
    ALL_TIME,
    LAST_24_HOURS,
    LAST_7_DAYS,
    LAST_30_DAYS;

    /**
     * Resolves this preset into a concrete [DatabaseExporter.ExportSelection]
     * anchored at [nowMillis].
     */
    fun toSelection(nowMillis: Long): DatabaseExporter.ExportSelection = when (this) {
        ALL_TIME -> DatabaseExporter.ExportSelection()
        LAST_24_HOURS -> window(nowMillis, TimeUnit.HOURS.toMillis(HOURS_PER_DAY))
        LAST_7_DAYS -> window(nowMillis, TimeUnit.DAYS.toMillis(DAYS_PER_WEEK))
        LAST_30_DAYS -> window(nowMillis, TimeUnit.DAYS.toMillis(DAYS_PER_MONTH))
    }

    private fun window(nowMillis: Long, windowMillis: Long): DatabaseExporter.ExportSelection =
        DatabaseExporter.ExportSelection(
            fromInclusiveMillis = nowMillis - windowMillis,
            // Keep the upper bound exclusive but still admit "now" itself.
            toExclusiveMillis = nowMillis + INCLUSIVE_NOW_OFFSET_MILLIS,
        )

    private companion object {
        const val HOURS_PER_DAY = 24L
        const val DAYS_PER_WEEK = 7L
        const val DAYS_PER_MONTH = 30L
        const val INCLUSIVE_NOW_OFFSET_MILLIS = 1L
    }
}
