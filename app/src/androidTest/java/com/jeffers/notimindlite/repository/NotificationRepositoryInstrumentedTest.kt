package com.jeffers.notimindlite.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.domain.entity.Resource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented test for NotificationRepository operations against SQLite. */
@RunWith(AndroidJUnit4::class)
class NotificationRepositoryInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao
    private lateinit var repository: NotificationRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setTestInstance(database)
        dao = database.notificationDao()
        repository = NotificationRepository(context)
    }

    @After
    fun teardown() {
        AppDatabase.resetInstance()
        database.close()
    }

    @Test
    fun getAllNotifications_returnsAllInsertedEntitiesInResourceSuccess() = runBlocking {
        val entity1 = createEntity(key = "repo_key_1", title = "Alert One")
        val entity2 = createEntity(key = "repo_key_2", title = "Alert Two")
        dao.insert(entity1)
        dao.insert(entity2)

        val result = repository.getAllNotifications()
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(2, data.size)
        assertTrue(data.any { it.key == "repo_key_1" })
        assertTrue(data.any { it.key == "repo_key_2" })
    }

    @Test
    fun getRecent_respectsLimitAndOrdersByPostTimeDescending() = runBlocking {
        val now = System.currentTimeMillis()
        val older = createEntity(key = "repo_older", title = "Older", postTime = now - 5000)
        val newer = createEntity(key = "repo_newer", title = "Newer", postTime = now)
        dao.insert(older)
        dao.insert(newer)

        val result = repository.getRecent(limit = 1)
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(1, data.size)
        assertEquals("repo_newer", data[0].key)
    }

    @Test
    fun getById_findsNotificationWhenPresentAndReturnsNullWhenAbsent() = runBlocking {
        val entity = createEntity(key = "repo_by_id", title = "Specific Notification")
        val generatedId = dao.insert(entity)

        val foundResult = repository.getById(generatedId)
        assertTrue(foundResult is Resource.Success)
        val foundEntity = (foundResult as Resource.Success).data
        assertNotNull(foundEntity)
        assertEquals("repo_by_id", foundEntity!!.key)
        assertEquals("Specific Notification", foundEntity.title)

        val missingResult = repository.getById(99999L)
        assertTrue(missingResult is Resource.Success)
        assertNull((missingResult as Resource.Success).data)
    }

    @Test
    fun searchFts_returnsMatchingNotificationsViaFtsIndex() = runBlocking {
        val matching = createEntity(
            key = "repo_fts_match",
            title = "Urgent Flight Update",
            content = "Gate changed to B22"
        )
        val nonMatching = createEntity(
            key = "repo_fts_other",
            title = "Recipe",
            content = "Chocolate cake instructions"
        )
        dao.insert(matching)
        dao.insert(nonMatching)

        val searchResult = repository.searchFts("Flight*")
        assertTrue(searchResult is Resource.Success)
        val results = (searchResult as Resource.Success).data
        assertEquals(1, results.size)
        assertEquals("repo_fts_match", results[0].key)
    }

    private fun createEntity(
        key: String,
        title: String,
        content: String = "Test content",
        postTime: Long = System.currentTimeMillis()
    ): NotificationEntity = NotificationEntity(
        key = key,
        packageName = "com.test.app",
        appName = "TestApp",
        title = title,
        content = content,
        postTime = postTime,
        isDismissed = false
    )
}
