package com.jeffers.notimindlite.data.maps

import com.jeffers.notimindlite.util.ActionableEntityExtractor

/**
 * GeminiMapsDetector identifies location-based entities within notification content.
 */
object GeminiMapsDetector {
    private val LOCATION_KEYWORDS = setOf(
        "street", "st", "avenue", "ave", "road", "rd", "boulevard", "blvd",
        "drive", "dr", "court", "ct", "place", "pl", "square", "sq",
        "highway", "hwy", "apartment", "apt", "suite", "ste", "floor", "fl",
        "lane", "ln", "way"
    )

    private val KEYWORDS_REGEX = LOCATION_KEYWORDS.joinToString("|")

    private val ADDRESS_PATTERN = Regex(
        "\\b(\\d{1,5}\\s+[A-Za-z0-9\\s]+?\\b(?:$KEYWORDS_REGEX)\\b)", 
        RegexOption.IGNORE_CASE
    )

    fun detect(text: String): List<ActionableEntityExtractor.ActionableEntity> {
        val foundEntities = mutableListOf<ActionableEntityExtractor.ActionableEntity>()
        
        val matches = ADDRESS_PATTERN.findAll(text)
        for (match in matches) {
            val rawText = match.value.trim()
            foundEntities.add(
                ActionableEntityExtractor.ActionableEntity(
                    value = rawText,
                    type = ActionableEntityExtractor.EntityType.LOCATION,
                    range = match.range
                )
            )
        }

        return foundEntities
    }
}
