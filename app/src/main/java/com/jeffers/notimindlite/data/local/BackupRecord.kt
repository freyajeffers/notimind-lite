package com.jeffers.notimindlite.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BackupRecord tracks the history of backup operations (Export/Import).
 * This ensures that only authorized, internally-generated backups are imported.
 */
@Entity(tableName = "backup_records")
data class BackupRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // "EXPORT" or "IMPORT"
    val fileHash: String,
    val signature: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fileName: String? = null,
    val logMessage: String? = null
)
