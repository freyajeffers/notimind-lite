package com.jeffers.notimindlite.data.maps

import com.jeffers.notimindlite.util.ActionableEntityExtractor

/**
 * GeminiMapsDetector identifies location-based entities within notification content.
 */
object GeminiMapsDetector {
    private val ADDRESS_PATTERN = Regex(
        "\\b(\\d{1,5}\\s+[A-Za-z0-9\\s,.]+)\\b", 
        RegexOption.IGNORE_CASE
    )
    
    private val LOCATION_KEYWORDS = setOf(
        "street", "st", "avenue", "ave", "road", "rd", "boulevard", "blvd",
        "drive", "dr", "court", "ct", "place", "pl", "square", "sq",
        "highway", "hwy", "apartment", "apt", "suite", "ste", "floor", "fl"
    )

    fun detect(text: String): List<ActionableEntityExtractor.ActionableEntity> {
        val foundEntities = mutableListOf<ActionableEntityExtractor.ActionableEntity>()
        
        val matches = ADDRESS_PATTERN.findAll(text)
        for (match in matches) {
            val rawText = match.value.trim()
            if (isLikelyLocation(rawText)) {
                foundEntities.add(
                    ActionableEntityExtractor.ActionableEntity(
                        value = rawText,
                        type = ActionableEntityExtractor.EntityType.LOCATION,
                        range = match.range
                    )
                )
            }
        }

        return foundEntities
    }

    private fun isLikelyLocation(text: String): Boolean {
        val words = text.lowercase().split(" ")
        val hasKeyword = words.any { it in LOCATION_KEYWORDS }
        val hasZip = text.contains(Regex("\\b\\d{5}(-\\d{4})?\\b"))
        val hasComma = text.contains(",")

        return hasKeyword || hasZip || hasComma
    }
}
