package com.jeffers.notimindlite.util

/**
 * Decides which notifications an export covers.
 *
 * [DatabaseExporter.ExportSelection] treats an empty package set as "every app", but the
 * export UI treats an empty selection as "the user unticked everything". Passing the UI
 * state straight through therefore exported the entire log after the user cleared all
 * apps. This is the single place that reconciles the two meanings.
 */
object ExportSelectionPolicy {
    /**
     * @return the selection to export, or `null` when the user has apps available but
     *   selected none of them — meaning nothing may be exported.
     */
    fun selection(
        availablePackageCount: Int,
        selectedPackages: Set<String>,
        preset: ExportRangePreset,
        nowMillis: Long,
    ): DatabaseExporter.ExportSelection? {
        val nothingSelected = availablePackageCount > 0 && selectedPackages.isEmpty()
        if (nothingSelected) return null
        return preset.toSelection(nowMillis).copy(packageNames = selectedPackages)
    }
}
