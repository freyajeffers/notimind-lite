package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndFtsSearch_returnsMatchingNotification() = runBlocking {
        val entity = NotificationEntity(
            key = "fts_key_1",
            packageName = "com.banking.app",
            appName = "SecureBank",
            title = "Security Alert Transfer",
            content = "Wire transfer of 500 dollars completed",
            postTime = System.currentTimeMillis(),
            isDismissed = false
        )
        dao.insert(entity)

        val searchResults = dao.searchNotificationsFtsSync("Transfer*")
        assertNotNull(searchResults)
        assertEquals(1, searchResults.size)
        assertEquals("fts_key_1", searchResults[0].key)
        assertEquals("Security Alert Transfer", searchResults[0].title)
    }

    @Test
    fun markAsReadAndUnreadCount_updatesReadStateAccurately() = runBlocking {
        val entity1 = NotificationEntity(
            key = "unread_1",
            packageName = "com.chat.app",
            appName = "ChatApp",
            title = "New Message",
            content = "Hello there",
            isRead = false,
            isDismissed = false
        )
        val entity2 = NotificationEntity(
            key = "unread_2",
            packageName = "com.chat.app",
            appName = "ChatApp",
            title = "Another Message",
            content = "Are you available?",
            isRead = false,
            isDismissed = false
        )
        dao.insert(entity1)
        dao.insert(entity2)

        val initialUnread = dao.getUnreadCountFlow().first()
        assertEquals(2, initialUnread)

        dao.markAsRead("unread_1")
        val updatedUnread = dao.getUnreadCountFlow().first()
        assertEquals(1, updatedUnread)

        dao.markAllAsRead()
        val finalUnread = dao.getUnreadCountFlow().first()
        assertEquals(0, finalUnread)
    }

    @Test
    fun pruneOldLogs_deletesDismissedUnpinnedBeforeCutoff() = runBlocking {
        val now = System.currentTimeMillis()
        val oldDismissed = NotificationEntity(
            key = "old_dismissed",
            packageName = "com.news.feed",
            appName = "News",
            title = "Old News",
            content = "Old content",
            postTime = now - 100_000L,
            isDismissed = true,
            isPinned = false
        )
        val oldPinned = NotificationEntity(
            key = "old_pinned",
            packageName = "com.important.app",
            appName = "Important",
            title = "Pinned Alert",
            content = "Never delete this",
            postTime = now - 100_000L,
            isDismissed = true,
            isPinned = true
        )
        val recentDismissed = NotificationEntity(
            key = "recent_dismissed",
            packageName = "com.news.feed",
            appName = "News",
            title = "Fresh News",
            content = "Just happened",
            postTime = now - 1_000L,
            isDismissed = true,
            isPinned = false
        )

        dao.insert(oldDismissed)
        dao.insert(oldPinned)
        dao.insert(recentDismissed)

        dao.pruneOldLogs(cutoffTimeMs = now - 50_000L)

        val remaining = dao.getAllNotificationsList()
        val keys = remaining.map { it.key }
        assertFalse(keys.contains("old_dismissed"))
        assertTrue(keys.contains("old_pinned"))
        assertTrue(keys.contains("recent_dismissed"))
    }

    @Test
    fun batchPinnedStatusAndDeletion_modifiesEntitiesCorrectly() = runBlocking {
        val entityA = NotificationEntity(
            key = "key_a",
            packageName = "com.app.a",
            appName = "AppA",
            title = "Alpha",
            content = "Content A",
            isPinned = false
        )
        val entityB = NotificationEntity(
            key = "key_b",
            packageName = "com.app.b",
            appName = "AppB",
            title = "Beta",
            content = "Content B",
            isPinned = false
        )
        dao.insert(entityA)
        dao.insert(entityB)

        dao.updatePinnedStatusBatch(listOf("key_a", "key_b"), isPinned = true)
        val allAfterPin = dao.getAllNotificationsList()
        assertTrue(allAfterPin.all { it.isPinned })

        dao.deleteByKey("key_a")
        val remaining = dao.getAllNotificationsList()
        assertEquals(1, remaining.size)
        assertEquals("key_b", remaining[0].key)
    }
}
