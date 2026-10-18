package com.jeffers.notimindlite.domain.clustering

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

private const val TAG_HELPERS = "DynamicClusterHelpers"

internal fun applicationLabel(packageManager: PackageManager, app: ApplicationInfo): String? {
    return try {
        packageManager.getApplicationLabel(app).toString().lowercase()
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG_HELPERS, "Application label lookup failed for ${app.packageName}", e)
        null
    }
}

internal fun appDomain(app: ApplicationInfo, packageName: String, label: String?): String? {
    return if (app.category != ApplicationInfo.CATEGORY_UNDEFINED) {
        DynamicClusterManager.OS_CATEGORY_MAPPING[app.category]
    } else {
        inferDomainFromPackageName(packageName, label)
    }
}

internal fun meaningfulPackageParts(packageName: String): List<String> {
    return packageName.split(".").filter {
        it !in setOf("com", "android", "app", "mobile", "apps") && it.length > 2
    }
}

internal fun appKeywords(label: String?, meaningfulParts: List<String>): List<String> {
    val labelWords = label?.split("\\s+".toRegex())?.filter { it.length > 2 }.orEmpty()
    return listOfNotNull(label) + labelWords + meaningfulParts
}

internal fun addMatchingSubcategoryKeywords(
    domainNode: ClusterNode,
    combined: String,
    appLabel: String?,
    meaningfulParts: List<String>
) {
    domainNode.subClusters.values
        .filter { subNode -> subNode.keywords.any(combined::contains) }
        .forEach { subNode ->
            appLabel?.let(subNode.keywords::add)
            subNode.keywords.addAll(meaningfulParts)
        }
}

internal fun inferDomainFromPackageName(pkgName: String, appLabel: String?): String? {
    val combined = "$pkgName ${appLabel ?: ""}".lowercase()
    return DynamicClusterManager.PACKAGE_DOMAIN_MARKERS.firstOrNull { (_, markers) ->
        markers.any(combined::contains)
    }?.first
}
