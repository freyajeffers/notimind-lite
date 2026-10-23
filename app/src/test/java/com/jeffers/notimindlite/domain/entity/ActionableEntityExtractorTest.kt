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

    @Test
    fun extract_location_detectsStreetAddressAndReturnsLocationEntity() {
        val text = "Pick up your order at 789 Broadway Ave this afternoon"
        val entities = ActionableEntityExtractor.extract(text)

        val locationEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.LOCATION }
        assertEquals(1, locationEntities.size)
        assertEquals("789 Broadway Ave", locationEntities[0].value)
    }

    @Test
    fun extract_allThreeTypes_detectsUrlOtpAndLocationInOrder() {
        val text = "Deliver to 100 Main St then open https://delivery.app and use pin 4567"
        val entities = ActionableEntityExtractor.extract(text)

        assertEquals(3, entities.size)
        assertEquals(ActionableEntityExtractor.EntityType.LOCATION, entities[0].type)
        assertEquals("100 Main St", entities[0].value)

        assertEquals(ActionableEntityExtractor.EntityType.URL, entities[1].type)
        assertEquals("https://delivery.app", entities[1].value)

        assertEquals(ActionableEntityExtractor.EntityType.OTP, entities[2].type)
        assertEquals("4567", entities[2].value)

        assertTrue(entities[0].range.first < entities[1].range.first)
        assertTrue(entities[1].range.first < entities[2].range.first)
    }

    @Test
    fun extract_urlsWithQueryAndFragment() {
        val text = "Track parcel at https://track.shipment.com/order?id=12345#status"
        val entities = ActionableEntityExtractor.extract(text)

        val urlEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.URL }
        assertEquals(1, urlEntities.size)
        assertEquals("https://track.shipment.com/order?id=12345#status", urlEntities[0].value)
    }

    @Test
    fun extract_numericTokensOutsideFourToEightDigits_areIgnored() {
        val text = "Order 123 has 123456789 items and room 9"
        val entities = ActionableEntityExtractor.extract(text)
        val otpEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.OTP }
        assertTrue(otpEntities.isEmpty())
    }

    @Test
    fun extract_alphanumericTokens_areNotExtractedAsOtp() {
        val text = "Reference code ABC1234 or USER9999 was generated"
        val entities = ActionableEntityExtractor.extract(text)
        val otpEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.OTP }
        assertTrue(otpEntities.isEmpty())
    }

    @Test
    fun extract_urlWithPunctuation_excludesTrailingPunctuation() {
        val text = "Visit https://example.com/api, or check https://example.com/test!"
        val entities = ActionableEntityExtractor.extract(text)
        val urlEntities = entities.filter { it.type == ActionableEntityExtractor.EntityType.URL }

        assertEquals(2, urlEntities.size)
        assertEquals("https://example.com/api", urlEntities[0].value)
        assertEquals("https://example.com/test", urlEntities[1].value)
    }

    @Test
    fun extract_rangeIndices_matchExactSubstringInOriginalText() {
        val text = "Use verification 883921 on https://auth.provider.org now"
        val entities = ActionableEntityExtractor.extract(text)

        assertEquals(2, entities.size)
        for (entity in entities) {
            val substring = text.substring(entity.range.first, entity.range.last + 1)
            assertEquals(entity.value, substring)
        }
    }
}
