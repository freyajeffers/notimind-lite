package com.jeffers.notimindlite.sanitization

/**
 * SanitizationPipeline orchestrates package filtering and PII redaction for
 * notification fields. It is deterministic and testable. It returns a
 * SanitizationResult with sanitized fields, or null to indicate the notification
 * must be dropped (fail-closed).
 */
data class SanitizationResult(
    val title: String,
    val content: String,
    val subText: String?,
    val bigText: String?
)

class SanitizationPipeline(
    private val packageFilterManager: PackageFilterManager = PackageFilterManager.default(),
    private val redactor: PiiRedactionEngine = PiiRedactionEngine
) {

    /**
     * Returns SanitizationResult or null to drop.
     */
    fun sanitize(
        packageName: String?,
        title: String?,
        content: String?,
        subText: String?,
        bigText: String?
    ): SanitizationResult? {
        // If redaction is disabled through settings, short-circuit and return raw fields (fallback empty strings to avoid nulls)
        val pref = com.jeffers.notimindlite.data.local.PreferenceManager(effectiveContext)
        if (!pref.isPiiRedactionEnabled()) {
            val t = title ?: ""
            val c = content ?: ""
            val s = subText
            val b = bigText
            if (!packageFilterManager.shouldAccept(packageName)) return null
            return SanitizationResult(t, c, s, b)
        }
    }
}
