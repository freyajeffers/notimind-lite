package com.jeffers.notimindlite.ui.screens

import android.content.Context
import android.content.pm.PackageManager

/** Returns only observed packages that are currently installed. */
fun installedObservedApps(
    context: Context,
    observedApps: List<Pair<String, String>>
): List<Pair<String, String>> {
    val installedPackages = observedApps
        .mapNotNull { (packageName, _) ->
            runCatching {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
                packageName
            }.getOrNull()
        }.toSet()
    return filterInstalledObservedApps(observedApps, installedPackages)
}

internal fun filterInstalledObservedApps(
    observedApps: List<Pair<String, String>>,
    installedPackages: Set<String>
): List<Pair<String, String>> =
    observedApps
        .distinctBy { it.first }
        .filter { it.first in installedPackages }
