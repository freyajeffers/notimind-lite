package com.jeffers.notimindlite.data.local

/**
 * Default capture rules.
 *
 * These packages used to be filtered out inside the UI by a hardcoded set, which meant the
 * user could not un-block a system notification source they actually wanted. They are now
 * seeded into the user's own blocklist so the same default behaviour applies, but every
 * entry is visible and editable in Settings.
 */
object CaptureDefaults {
    /** System noise that would otherwise flood the log with charging/USB/debug chatter. */
    val BLOCKED_PACKAGES: List<String> = listOf(
        "android",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox"
    )

    val BLOCKED_PACKAGES_CSV: String = BLOCKED_PACKAGES.joinToString(",")

    /** Pref marker so clearing the list stays cleared instead of re-seeding on next launch. */
    const val KEY_BLOCKLIST_SEEDED = "config_capture_package_blocklist_seeded"
}
