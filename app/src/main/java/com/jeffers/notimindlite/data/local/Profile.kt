package com.jeffers.notimindlite.data.local

/** A local account/profile. Settings and the Room database are isolated by [id]. */
data class Profile(
    val id: String,
    val name: String,
    val createdAt: Long,
)