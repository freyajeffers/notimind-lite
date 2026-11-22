package com.jeffers.notimindlite.ui.screens

/**
 * App selectors are record-driven, not install-driven.
 *
 * An app appears in a selector when it has at least one persisted notification, whether
 * or not it is still installed. Apps with no records are hidden. Installation state is
 * deliberately ignored: uninstalling an app must not make its history disappear from the
 * selectors that are used to review that history.
 */
fun observedAppsWithRecords(
    observedApps: List<Pair<String, String>>,
    packagesWithRecords: Set<String>,
): List<Pair<String, String>> =
    observedApps
        .distinctBy { it.first }
        .filter { it.first in packagesWithRecords }
