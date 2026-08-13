package com.jeffers.notimindlite.data.local

/**
 * Encapsulates multi-criteria filter parameters for notification searching.
 */
data class NotificationFilter(
    val query: String? = null,
    val packageNames: List<String>? = null,
    val channelId: String? = null,
    val minImportance: Int? = null,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val isClearable: Boolean? = null,
    val isDismissed: Boolean? = null,
    val isPinned: Boolean? = null,
    val dismissReason: Int? = null
)
