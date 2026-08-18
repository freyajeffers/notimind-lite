package com.jeffers.notimindlite.util

import java.util.regex.Pattern

/**
 * ActionableEntityExtractor provides regex-based detection for common entities
 * in notification content, such as OTP codes and URLs.
 */
object ActionableEntityExtractor {
    // Matches common OTP patterns: 4-8 digits, often prefixed by 'code' or 'OTP'
    private val OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b")
    
    // Matches standard URLs (http, https, www)
    private val URL_PATTERN = Pattern.compile(
        "(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])",
        Pattern.CASE_INSENSITIVE
    )

    data class ActionableEntity(
        val value: String,
        val type: EntityType,
        val range: IntRange
    )

    enum class EntityType {
        OTP, URL, LOCATION
    }

    /**
     * Extracts actionable entities from the provided text.
     */
    fun extract(text: String): List<ActionableEntity> {
        if (text.isBlank()) return emptyList()
        
        val entities = mutableListOf<ActionableEntity>()
        
        // Extract URLs
        val urlMatcher = URL_PATTERN.matcher(text)
        while (urlMatcher.find()) {
            entities.add(ActionableEntity(urlMatcher.group(), EntityType.URL, urlMatcher.start()..urlMatcher.end()-1))
        }
        
        // Extract OTPs
        val otpMatcher = OTP_PATTERN.matcher(text)
        while (otpMatcher.find()) {
            entities.add(ActionableEntity(otpMatcher.group(), EntityType.OTP, otpMatcher.start()..otpMatcher.end()-1))
        }
        
        // Detect Locations via GeminiMapsDetector
        val locations = com.jeffers.notimindlite.data.maps.GeminiMapsDetector.detect(text)
        entities.addAll(locations)
        
        return entities.sortedBy { it.range.first }
    }
}
