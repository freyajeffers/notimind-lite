package com.jeffers.notimindlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateApp(app: AppEntity): Long

    @Query("SELECT * FROM apps ORDER BY appName ASC")
    fun getAllAppsFlow(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackage(packageName: String): AppEntity?
}
