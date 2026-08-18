package com.jeffers.notimindlite.data.maps

/**
 * Represents a location entity detected within notification text.
 */
data class DetectedPlace(
    val rawText: String,
    val normalizedAddress: String? = null,
    val placeId: String? = null,
    val type: PlaceType = PlaceType.GENERAL,
    val confidence: Float = 1.0f
)

/**
 * Categorization of detected locations to determine the appropriate action chip.
 */
enum class PlaceType {
    GENERAL,        // Generic address or city
    COMMERCIAL,     // Stores, restaurants, offices
    RESIDENTIAL,    // Home, apartment
    TRANSPORT,      // Airports, stations, stops
    LANDMARK       // Famous monuments, parks, museums
}
