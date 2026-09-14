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
        // Reject on package filter
        if (!packageFilterManager.shouldAccept(packageName)) return null

        // Redact required summary fields: title and content
        val sTitle = redactor.redact(title ?: "") ?: return null
        val sContent = redactor.redact(content ?: "") ?: return null

        val sSub = redactor.redact(subText)
        val sBig = redactor.redact(bigText)

        return SanitizationResult(sTitle, sContent, sSub, sBig)
    }
}
