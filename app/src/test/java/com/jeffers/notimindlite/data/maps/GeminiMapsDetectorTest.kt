package com.jeffers.notimindlite.data.maps

import com.jeffers.notimindlite.domain.entity.ActionableEntityExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMapsDetectorTest {
    @Test
    fun detect_emptyOrBlankInput_returnsEmptyList() {
        assertTrue(GeminiMapsDetector.detect("").isEmpty())
        assertTrue(GeminiMapsDetector.detect("   \t\n  ").isEmpty())
    }

    @Test
    fun detect_shouldFindValidAddressWithKeyword() {
        val text = "Your delivery is arriving at 123 Maple St, Springfield"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(1, entities.size)
        assertEquals("123 Maple St", entities[0].value)
        assertEquals(ActionableEntityExtractor.EntityType.LOCATION, entities[0].type)
    }

    @Test
    fun detect_shouldFindValidAddressWithZipCode() {
        val text = "Visit us at 456 Oak Lane 90210"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(1, entities.size)
        assertTrue(entities[0].value.contains("456 Oak Lane"))
    }

    @Test
    fun detect_shouldIgnoreNonAddressNumbers() {
        val text = "Call 555-0199 for details"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(0, entities.size)
    }

    @Test
    fun detect_shouldFindMultipleLocations() {
        val text = "Meet at 100 Main St and then go to 200 Second Ave"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(2, entities.size)
        assertEquals("100 Main St", entities[0].value)
        assertEquals("200 Second Ave", entities[1].value)
    }

    @Test
    fun detect_variousRoadTypesAndCaseInsensitive() {
        val text = "Office is at 500 Sunset BLVD and warehouse at 42 Ocean HWY"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(2, entities.size)
        assertEquals("500 Sunset BLVD", entities[0].value)
        assertEquals("42 Ocean HWY", entities[1].value)
    }

    @Test
    fun detect_accurateTextRanges() {
        val text = "Start at 10 Elm Road and stop"
        val entities = GeminiMapsDetector.detect(text)

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals("10 Elm Road", entity.value)
        assertEquals(text.indexOf("10 Elm Road"), entity.range.first)
        assertEquals(text.indexOf("10 Elm Road") + "10 Elm Road".length - 1, entity.range.last)
    }
}
