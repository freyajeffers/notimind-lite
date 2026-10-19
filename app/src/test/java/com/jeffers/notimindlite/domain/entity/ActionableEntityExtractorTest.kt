package com.jeffers.notimindlite.domain.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionableEntityExtractorTest {
    @Test
    fun extract_emptyOrBlankText_returnsEmptyList() {
        assertTrue(ActionableEntityExtractor.extract("").isEmpty())
        assertTrue(ActionableEntityExtractor.extract("   \t\n").isEmpty())
    }

    @Test
    fun extract_otpCodes_identifiesNumericTokensCorrectly() {
        val text = "Your verification code is 482910 for NotiMind login."
        val entities = ActionableEntityExtractor.extract(text)

        val otpEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.OTP }
        assertEquals(1, otpEntities.size)
        assertEquals("482910", otpEntities[0].value)
        assertEquals(text.indexOf("482910"), otpEntities[0].range.first)
    }

    @Test
    fun extract_multipleOtps_extractsAllValidLengths() {
        val text = "Old code: 1234, new code: 87654321."
        val entities = ActionableEntityExtractor.extract(text)
        val otpEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.OTP }

        assertEquals(2, otpEntities.size)
        assertEquals("1234", otpEntities[0].value)
        assertEquals("87654321", otpEntities[1].value)
    }

    @Test
    fun extract_url_identifiesHttpAndHttps() {
        val text = "Check out https://github.com/freyajeffers/notimind-lite for updates or http://example.com"
        val entities = ActionableEntityExtractor.extract(text)
        val urlEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.URL }

        assertEquals(2, urlEntities.size)
        assertEquals("https://github.com/freyajeffers/notimind-lite", urlEntities[0].value)
        assertEquals("http://example.com", urlEntities[1].value)
    }

    @Test
    fun extract_mixedEntities_sortedByRangeStart() {
        val text = "Go to https://noti.mind/verify and enter code 993122 to continue"
        val entities = ActionableEntityExtractor.extract(text)

        assertEquals(2, entities.size)
        assertEquals(ActionableEntityExtractor.EntityType.URL, entities[0].type)
        assertEquals("https://noti.mind/verify", entities[0].value)
        assertEquals(ActionableEntityExtractor.EntityType.OTP, entities[1].type)
        assertEquals("993122", entities[1].value)
        assertTrue(entities[0].range.first < entities[1].range.first)
    }

    @Test
    fun extract_textWithoutEntities_returnsEmptyList() {
        val text = "Just a regular plain text message with no links or codes."
        val entities = ActionableEntityExtractor.extract(text)
        assertTrue(entities.isEmpty())
    }
}
