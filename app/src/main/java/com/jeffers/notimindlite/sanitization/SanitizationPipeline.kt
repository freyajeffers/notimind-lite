package com.jeffers.notimindlite.sanitization

import android.content.Context

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
    private val context: Context? = null,
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
        val isRedactionEnabled = context?.let {
            com.jeffers.notimindlite.data.local.PreferencesRepository(it).redactPii.value
        } ?: true

        if (!packageFilterManager.shouldAccept(packageName)) return null

        if (!isRedactionEnabled) {
            return SanitizationResult(
                title = title ?: "",
                content = content ?: "",
                subText = subText,
                bigText = bigText
            )
        }

        val sanitizedTitle = redactor.redact(title ?: "") ?: return null
        val sanitizedContent = redactor.redact(content ?: "") ?: return null
        val sanitizedSubText = subText?.let { redactor.redact(it) }
        val sanitizedBigText = bigText?.let { redactor.redact(it) }

        return SanitizationResult(
            title = sanitizedTitle,
            content = sanitizedContent,
            subText = sanitizedSubText,
            bigText = sanitizedBigText
        )
    }
}
