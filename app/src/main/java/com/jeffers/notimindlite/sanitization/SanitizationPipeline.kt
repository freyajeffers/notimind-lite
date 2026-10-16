package com.jeffers.notimindlite.sanitization

import android.content.Context
import com.jeffers.notimindlite.data.local.PreferencesRepository

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
        // Return raw fields when redaction is disabled; nulls become empty strings.
        val isRedactionEnabled = context?.let {
            PreferencesRepository(it).securityManager.redactPii.value
        } ?: false

        return when {
            !packageFilterManager.shouldAccept(packageName) -> null
            !isRedactionEnabled -> rawResult(title, content, subText, bigText)
            else -> redactedResult(title, content, subText, bigText)
        }
    }

    private fun rawResult(
        title: String?,
        content: String?,
        subText: String?,
        bigText: String?
    ): SanitizationResult = SanitizationResult(
        title = title.orEmpty(),
        content = content.orEmpty(),
        subText = subText,
        bigText = bigText
    )

    private fun redactedResult(
        title: String?,
        content: String?,
        subText: String?,
        bigText: String?
    ): SanitizationResult? {
        val redactedTitle = redactor.redact(title.orEmpty())
        val redactedContent = redactor.redact(content.orEmpty())
        val redactedSubText = subText?.let(redactor::redact)
        val redactedBigText = bigText?.let(redactor::redact)
        val requiredFields = listOfNotNull(redactedTitle, redactedContent)
        return requiredFields.takeIf { it.size == 2 }?.let {
            SanitizationResult(it[0], it[1], redactedSubText, redactedBigText)
        }
    }
}
