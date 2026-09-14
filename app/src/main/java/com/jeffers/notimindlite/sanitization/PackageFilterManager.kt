package com.jeffers.notimindlite.sanitization

/**
 * PackageFilterManager maintains a small, testable set of package allow/deny rules.
 * It is intentionally simple and deterministic.
 */
class PackageFilterManager(private val blacklist: Set<String> = emptySet(), private val whitelist: Set<String> = emptySet()) {

    /**
     * Returns true if the package should be accepted (ingested). Rules:
     * - If whitelist is non-empty, only packages in whitelist are accepted.
     * - Otherwise, packages in blacklist are rejected.
     */
    fun shouldAccept(packageName: String?): Boolean {
        if (packageName == null) return false
        if (whitelist.isNotEmpty()) {
            return whitelist.contains(packageName)
        }
        return !blacklist.contains(packageName)
    }

    companion object {
        fun default(): PackageFilterManager {
            // example runtime blacklist; production should load from config
            return PackageFilterManager(blacklist = setOf("com.android.shell", "com.google.android.googlequicksearchbox"))
        }
    }
}
