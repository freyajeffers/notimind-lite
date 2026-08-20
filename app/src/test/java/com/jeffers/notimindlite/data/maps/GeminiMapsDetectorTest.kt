package com.jeffers.notimindlite

import com.jeffers.notimindlite.data.maps.GeminiMapsDetector
import com.jeffers.notimindlite.util.ActionableEntityExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMapsDetectorTest {

    @Test
    fun `detect should find valid address with keyword`() {
        val text = "Your delivery is arriving at 123 Maple St, Springfield"
        val entities = GeminiMapsDetector.detect(text)
        
        assertEquals(1, entities.size)
        assertEquals("123 Maple St", entities[0].value)
        assertEquals(ActionableEntityExtractor.EntityType.LOCATION, entities[0].type)
    }

    @Test
    fun `detect should find valid address with zip code`() {
        val text = "Visit us at 456 Oak Lane 90210"
        val entities = GeminiMapsDetector.detect(text)
        
        assertEquals(1, entities.size)
        assertTrue(entities[0].value.contains("456 Oak Lane"))
    }

    @Test
    fun `detect should ignore non-address numbers`() {
        val text = "Call 555-0199 for details"
        val entities = GeminiMapsDetector.detect(text)
        
        assertEquals(0, entities.size)
    }

    @Test
    fun `detect should find multiple locations`() {
        val text = "Meet at 100 Main St and then go to 200 Second Ave"
        val entities = GeminiMapsDetector.detect(text)
        
        assertEquals(2, entities.size)
        assertEquals("100 Main St", entities[0].value)
        assertEquals("200 Second Ave", entities[1].value)
    }
}
