package com.jeffers.notimindlite.domain.clustering

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.jeffers.notimindlite.util.AppLogger

private const val TAG_HELPERS = "DynamicClusterHelpers"
private const val MINIMUM_PREFIX_MARKER_LENGTH = 4
private val DOMAIN_TOKEN_SEPARATOR = Regex("[^a-z0-9]+")

internal fun applicationLabel(packageManager: PackageManager, app: ApplicationInfo): String? = try {
    packageManager.getApplicationLabel(app).toString().lowercase()
} catch (e: PackageManager.NameNotFoundException) {
    AppLogger.w(TAG_HELPERS, "Application label lookup failed for ${app.packageName}", e)
    null
}

internal fun appDomain(app: ApplicationInfo, packageName: String, label: String?): String? =
    if (app.category != ApplicationInfo.CATEGORY_UNDEFINED) {
        DynamicClusterManager.OS_CATEGORY_MAPPING[app.category]
    } else {
        inferDomainFromPackageName(packageName, label)
    }

internal fun meaningfulPackageParts(packageName: String): List<String> = packageName.split(".").filter {
    it !in setOf("com", "android", "app", "mobile", "apps") && it.length > 2
}

internal fun appKeywords(label: String?, meaningfulParts: List<String>): List<String> {
    val usableLabel = label?.takeIf { it.isNotBlank() }
    val labelWords = usableLabel?.split("\\s+".toRegex())?.filter { it.length > 2 }.orEmpty()
    return listOfNotNull(usableLabel) + labelWords + meaningfulParts
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
    val tokens = combined.split(DOMAIN_TOKEN_SEPARATOR).filter { it.isNotEmpty() }
    return DynamicClusterManager.PACKAGE_DOMAIN_MARKERS
        .firstOrNull { (_, markers) ->
            markers.any { marker ->
                tokens.any { token ->
                    token == marker ||
                        (marker.length >= MINIMUM_PREFIX_MARKER_LENGTH && token.startsWith(marker))
                }
            }
        }?.first
}
