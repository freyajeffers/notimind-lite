package com.jeffers.notimindlite.sanitization

object PiiRedactionEngine {
    private const val MIN_CARD_DIGITS = 12
    private const val MAX_CARD_DIGITS = 19
    private const val LUHN_BASE = 10
    private const val LUHN_DOUBLE = 2
    private const val LUHN_REDUCTION = 9
    // Deterministic, fast compiled patterns.
    private val otpRegex = Regex("\\b\\d{4,6}\\b")
    private val emailRegex = Regex(
        "[A-Za-z0-9._%+-]+@" +
            "[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    )
    // More permissive phone matcher: international optional, digits with separators
    private val phoneRegex = Regex(
        "(?:\\+?\\d" +
            "[\\d\\s().-]{7,}" +
            "\\d)"
    )
    // Currency: $ with optional spaces, digits, optional thousands/grouping and cents
    private val currencyRegex = Regex(
        "\\$\\s?\\d{1,3}(?:[,\\.]\\d{3})*(?:\\.\\d{2})?"
    )
    // Credit-card candidate: sequences of digits with optional spaces/dashes (12-19 digits total)
    private val ccCandidateRegex = Regex(
        "(?:\\d[ -]?){12,19}"
    )

    // Redaction placeholders
    private const val OTP_REPLACEMENT = "[REDACTED-OTP]"
    private const val EMAIL_REPLACEMENT = "[REDACTED-EMAIL]"
    private const val PHONE_REPLACEMENT = "[REDACTED-PHONE]"
    private const val CURRENCY_REPLACEMENT = "[REDACTED-CURRENCY]"
    private const val CC_REPLACEMENT = "[REDACTED-CC]"

    // Public API: return sanitized string, or null to indicate a fail-closed drop.
    // Deterministic: same input -> same output.
    fun redact(input: String?): String? {
        // Fail-closed flag: when true the final result must be null
        var failClosed = false
        var out = input ?: run {
            // treat null input as a fail-closed drop but continue to the unified return
            failClosed = true
            ""
        }

        // Quick rejection: if the entire string looks like a single CC or OTP, mark for drop
        val trimmed = out.trim()
        if (trimmed.isNotEmpty() && ccCandidateRegex.matches(trimmed)) {
            val digitsOnly = trimmed.filter { it.isDigit() }
            if (isValidLuhn(digitsOnly)) {
                failClosed = true
            } else {
                // otherwise mask in-place
                out = out.replace(ccCandidateRegex, CC_REPLACEMENT)
            }
        }

        // Replace credit-card-like sequences within text (mask if Luhn-valid)
        out = out.replace(ccCandidateRegex) { mr ->
            val candidate = mr.value.replace(Regex("[ -]"), "")
            if (isValidLuhn(candidate)) CC_REPLACEMENT else CC_REPLACEMENT
        }

        // Replace phone and currency first so their numeric fragments don't match OTP regex
        out = out.replace(phoneRegex, PHONE_REPLACEMENT)
        out = out.replace(currencyRegex, CURRENCY_REPLACEMENT)

        // Short numeric tokens (OTP)
        out = out.replace(otpRegex, OTP_REPLACEMENT)

        // Emails
        out = out.replace(emailRegex, EMAIL_REPLACEMENT)

        // Collapse adjacent placeholder artifacts into a single phone placeholder.
        out = out.replace(Regex("""\[REDACTED-PHONE\][\s\p{Punct}]*\[REDACTED-OTP\]"""), PHONE_REPLACEMENT)

        // Ensure CSV-safe output: remove newlines and double embedded quotes
        return if (failClosed) null else escapeForCsv(out)
    }

    // Escape field for inclusion in a CSV cell: double quotes and remove newlines
    private fun escapeForCsv(s: String): String {
        return s.replace("\"", "\"\"").replace(Regex("[\r\n]+"), " ")
    }

    private fun isValidLuhn(digitsOnly: String): Boolean {
        val s = digitsOnly.filter { it.isDigit() }
        if (s.length < MIN_CARD_DIGITS || s.length > MAX_CARD_DIGITS) return false
        var sum = 0
        var alternate = false
        for (i in s.length - 1 downTo 0) {
            var n = s[i] - '0'
            if (alternate) {
                n *= LUHN_DOUBLE
                if (n > LUHN_BASE - 1) n -= LUHN_REDUCTION
            }
            sum += n
            alternate = !alternate
        }
        return sum % LUHN_BASE == 0
    }
}
