package com.jeffers.notimindlite.sanitization

object PiiRedactionEngine {
    // Deterministic, fast compiled patterns.
    private val otpRegex = Regex("\\b\\d{4,6}\\b")
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    // More permissive phone matcher: international optional, digits with separators
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s().-]{7,}\\d)")
    // Currency: $ with optional spaces, digits, optional thousands/grouping and cents
    private val currencyRegex = Regex("\\$\\s?\\d{1,3}(?:[,\\.]\\d{3})*(?:\\.\\d{2})?")
    // Credit-card candidate: sequences of digits with optional spaces/dashes (12-19 digits total)
    private val ccCandidateRegex = Regex("(?:\\d[ -]?){12,19}")

    // Redaction placeholders
    private const val OTP_REPLACEMENT = "[REDACTED-OTP]"
    private const val EMAIL_REPLACEMENT = "[REDACTED-EMAIL]"
    private const val PHONE_REPLACEMENT = "[REDACTED-PHONE]"
    private const val CURRENCY_REPLACEMENT = "[REDACTED-CURRENCY]"
    private const val CC_REPLACEMENT = "[REDACTED-CC]"

    // Public API: return sanitized string, or null to indicate a fail-closed drop.
    // Deterministic: same input -> same output.
    fun redact(input: String?): String? {
        if (input == null) return null
        var out = input

        try {
            // Quick rejection: if the entire string looks like a single CC or OTP, drop (fail-closed)
            val trimmed = out.trim()
            if (ccCandidateRegex.matches(trimmed)) {
                // If it's a single numeric token that is a valid CC (Luhn), drop
                val digitsOnly = trimmed.filter { it.isDigit() }
                if (isValidLuhn(digitsOnly)) return null
                // otherwise mask
                out = out.replace(ccCandidateRegex, CC_REPLACEMENT)
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

            // Post-process placeholders: collapse adjacent placeholder artifacts like "[REDACTED-PHONE]-[REDACTED-OTP]" -> "[REDACTED-PHONE]"
            out = out.replace(Regex("""\[REDACTED-PHONE\][\s\p{Punct}]*\[REDACTED-OTP\]"""), PHONE_REPLACEMENT)

            // Ensure CSV-safe output: remove newlines and double embedded quotes
            out = escapeForCsv(out)

            return out
        } catch (e: Exception) {
            // Any unexpected error => fail-closed: signal drop
            return null
        }
    }

    // Escape field for inclusion in a CSV cell: double quotes and remove newlines
    private fun escapeForCsv(s: String): String {
        return s.replace("\"", "\"\"").replace(Regex("[\r\n]+"), " ")
    }

    private fun isValidLuhn(digitsOnly: String): Boolean {
        val s = digitsOnly.filter { it.isDigit() }
        if (s.length < 12 || s.length > 19) return false
        var sum = 0
        var alternate = false
        for (i in s.length - 1 downTo 0) {
            var n = s[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
