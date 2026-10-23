package com.jeffers.notimindlite.sanitization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertNull("A single Luhn-valid CC token should cause a fail-closed drop (null)", out)
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

    @Test
    fun `csv escaping handles double quotes and newlines`() {
        val input = "Hello \"World\"\nNext Line\rAnother"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Hello \"\"World\"\" Next Line Another", out)
    }

    @Test
    fun `short numeric tokens under 4 digits are not redacted as OTP`() {
        val input = "Order 123 has arrived"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Order 123 has arrived", out)
    }

    @Test
    fun `multiple pii types redacted concurrently`() {
        val input = "User alice@test.com paid $45.00 via +1 800 555 0199 code 998811"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals(
            "User [REDACTED-EMAIL] paid [REDACTED-CURRENCY] via [REDACTED-PHONE] code [REDACTED-OTP]",
            out
        )
    }

    @Test
    fun `numeric tokens over 6 digits are not redacted as OTP`() {
        val input = "Account number is 1234567 in the system"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("Account number is 1234567 in the system", out)
    }

    @Test
    fun `empty or whitespace string returns unchanged`() {
        val empty = ""
        val whitespace = "   \t  "
        assertEquals("", PiiRedactionEngine.redact(empty))
        assertEquals("   \t  ", PiiRedactionEngine.redact(whitespace))
    }

    @Test
    fun `luhn invalid 16 digit card is masked rather than dropped`() {
        // 4111 1111 1111 1112 fails Luhn check
        val input = "4111 1111 1111 1112"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull("Luhn-invalid card should not be fail-closed dropped", out)
        assertEquals("[REDACTED-CC]", out)
    }

    @Test
    fun `adjacent phone and otp artifacts collapse into single phone placeholder`() {
        // e.g. a phone number followed by a numeric token
        val input = "+1 (555) 123-4567 1234"
        val out = PiiRedactionEngine.redact(input)
        assertNotNull(out)
        assertEquals("[REDACTED-PHONE]", out)
    }
}
