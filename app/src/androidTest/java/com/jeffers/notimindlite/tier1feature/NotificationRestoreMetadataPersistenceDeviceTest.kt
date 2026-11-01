package com.jeffers.notimindlite.tier1feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRestoreMetadataPersistenceDeviceTest {
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
    }

    @Test
    fun syncReplacementPreservesLocalRestoreMetadata() = runBlocking {
        val inMemoryDatabase = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            ).allowMainThreadQueries()
            .build()
        database = inMemoryDatabase
        inMemoryDatabase.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")

        val dao = inMemoryDatabase.notificationDao()
        val metadata = "{\"schemaVersion\":1,\"template\":\"messaging\"}"
        val single = notification("single-key", metadata)
        dao.insertNotification(single)
        dao.insertNotification(single.copy(content = "Updated from sync", restoreMetadataJson = null))

        val batch = notification("batch-key", metadata)
        dao.insertNotifications(listOf(batch))
        dao.insertNotifications(listOf(batch.copy(content = "Batch sync update", restoreMetadataJson = null)))

        assertEquals(metadata, dao.getNotificationByKey(single.key)?.restoreMetadataJson)
        assertNotNull(dao.getNotificationByKey(batch.key))
        assertEquals(metadata, dao.getNotificationByKey(batch.key)?.restoreMetadataJson)
    }

    private fun notification(key: String, metadata: String) = NotificationEntity(
        key = key,
        packageName = "com.jeffers.notimindlite.persistence_test",
        appName = "Persistence Test",
        title = "Original title",
        content = "Original body",
        restoreMetadataJson = metadata
    )
}
