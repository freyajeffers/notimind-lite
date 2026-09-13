package com.jeffers.notimindlite.sanitization

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class PiiRedactionEngineTest {

    @Test
    fun `redact email inside text`() {
        val input = "Contact me at alice@example.com for details"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Contact me at [REDACTED-EMAIL] for details", out)
    }

    @Test
    fun `redact phone number inside text`() {
        val input = "Call +1 (555) 123-4567 now"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Call [REDACTED-PHONE] now", out)
    }

    @Test
    fun `redact OTP numeric token`() {
        val input = "Your code is 482719"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Your code is [REDACTED-OTP]", out)
    }

    @Test
    fun `redact currency occurrences`() {
        val input = "Total: $98.50 was charged"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Total: [REDACTED-CURRENCY] was charged", out)
    }

    @Test
    fun `drop single credit card token when Luhn valid`() {
        // 4111 1111 1111 1111 is a common Luhn-valid test card
        val input = "4111 1111 1111 1111"
        val out = PiiRedactionEngine.redact(input)
        assertNull(out, "A single Luhn-valid CC token should cause a fail-closed drop (null)")
    }

    @Test
    fun `mask credit card when embedded in text`() {
        val input = "My card 4111 1111 1111 1111 expires soon"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        // Should contain redaction placeholder and not the raw digits
        assert(out!!.contains("[REDACTED-CC]"))
        assert(!out.contains("4111"))
    }

    @Test
    fun `null input returns null`() {
        val out = PiiRedactionEngine.redact(null)
        assertNull(out)
    }
}
