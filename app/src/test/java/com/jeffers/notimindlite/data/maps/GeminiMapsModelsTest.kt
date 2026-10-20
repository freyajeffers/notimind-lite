package com.jeffers.notimindlite.data.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMapsModelsTest {
    @Test
    fun placeType_hasAllExpectedEnumEntries() {
        val expected = setOf("GENERAL", "COMMERCIAL", "RESIDENTIAL", "TRANSPORT", "LANDMARK")
        val actual = PlaceType.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun detectedPlace_defaultValuesAreAppliedCorrectly() {
        val place = DetectedPlace(rawText = "123 Elm St")
        assertEquals("123 Elm St", place.rawText)
        assertNull(place.normalizedAddress)
        assertNull(place.placeId)
        assertEquals(PlaceType.GENERAL, place.type)
        assertEquals(1.0f, place.confidence, 0.0001f)
    }

    @Test
    fun detectedPlace_customValuesAndCopyFunctionality() {
        val original = DetectedPlace(
            rawText = "Grand Central Station",
            normalizedAddress = "89 E 42nd St, New York, NY",
            placeId = "place_gc_123",
            type = PlaceType.TRANSPORT,
            confidence = 0.95f
        )

        assertEquals("Grand Central Station", original.rawText)
        assertEquals("89 E 42nd St, New York, NY", original.normalizedAddress)
        assertEquals("place_gc_123", original.placeId)
        assertEquals(PlaceType.TRANSPORT, original.type)
        assertEquals(0.95f, original.confidence, 0.0001f)

        val updated = original.copy(confidence = 0.99f)
        assertEquals(0.99f, updated.confidence, 0.0001f)
        assertEquals(original.placeId, updated.placeId)
        assertTrue(original != updated)
    }
}
