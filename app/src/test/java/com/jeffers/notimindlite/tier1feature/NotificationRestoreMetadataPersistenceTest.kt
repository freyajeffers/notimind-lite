package com.jeffers.notimindlite.tier1feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationRestoreMetadataPersistenceTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        AppDatabase.resetInstance()
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            ).allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
    }

    @After
    fun tearDown() {
        database.close()
        AppDatabase.resetInstance()
    }

    @Test
    fun singleRemoteReplacementPreservesLocalOnlyRestoreMetadata() = kotlinx.coroutines.runBlocking {
        val original = notification("single-key", "{\"version\":1}")
        database.notificationDao().insertNotification(original)

        database.notificationDao().insertNotification(
            original.copy(content = "Updated by sync", restoreMetadataJson = null)
        )

        assertEquals(
            original.restoreMetadataJson,
            database.notificationDao().getNotificationByKey(original.key)?.restoreMetadataJson
        )
    }

    @Test
    fun batchRemoteReplacementPreservesLocalOnlyRestoreMetadata() = kotlinx.coroutines.runBlocking {
        val original = notification("batch-key", "{\"version\":1}")
        database.notificationDao().insertNotifications(listOf(original))

        database.notificationDao().insertNotifications(
            listOf(original.copy(content = "Updated by sync", restoreMetadataJson = null))
        )

        assertNotNull(database.notificationDao().getNotificationByKey(original.key))
        assertEquals(
            original.restoreMetadataJson,
            database.notificationDao().getNotificationByKey(original.key)?.restoreMetadataJson
        )
    }

    private fun notification(key: String, restoreMetadataJson: String) = NotificationEntity(
        key = key,
        packageName = "com.restore.test",
        appName = "Restore test",
        title = "Message",
        content = "Body",
        groupKey = "com.restore.test",
        restoreMetadataJson = restoreMetadataJson
    )
}
