package com.jeffers.notimindlite.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BackupRecord)

    @Query("SELECT * FROM backup_records WHERE fileHash = :hash LIMIT 1")
    suspend fun getRecordByHash(hash: String): BackupRecord?

    @Query("SELECT * FROM backup_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<BackupRecord>>

    @Query("DELETE FROM backup_records")
    suspend fun clearRecords()
}
