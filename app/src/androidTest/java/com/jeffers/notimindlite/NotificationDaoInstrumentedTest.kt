package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.Converters
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun embeddingIndexer_computesAndPersistsEmbeddingInDatabase() = runBlocking {
        val entity = NotificationEntity(
            key = "embedding_key_1",
            packageName = "com.banking.app",
            appName = "Bank",
            title = "Monthly Statement",
            content = "Your monthly statement for checking account is ready"
        )
        val id = dao.insert(entity)
        val inserted = dao.getNotificationById(id)
        assertNotNull(inserted)
        org.junit.Assert.assertNull(inserted?.embedding)

        val indexer = com.jeffers.notimindlite.util
            .NotificationEmbeddingIndexer()
        indexer.indexSingle(dao, inserted!!)

        val updated = dao.getNotificationById(id)
        assertNotNull(updated)
        assertNotNull(updated?.embedding)
        assertTrue(updated!!.embedding!!.isNotEmpty())
    }

    @Test
    fun markAsRead_withAutoDeleteOnRead_deletesNotificationRow() = runBlocking {
        val entity = NotificationEntity(
            key = "auto_delete_1",
            packageName = "com.chat.temp",
            appName = "Chat",
            title = "Ephemeral",
            content = "Will be deleted on read",
            isRead = false
        )
        dao.insert(entity)
        assertNotNull(dao.getNotificationByKey("auto_delete_1"))

        dao.markAsRead("auto_delete_1", autoDeleteOnRead = true)
        assertNull(dao.getNotificationByKey("auto_delete_1"))
    }

    @Test
    fun markAsReadBatch_withAutoDeleteOnRead_deletesBatchRows() = runBlocking {
        val notif1 = NotificationEntity(
            key = "batch_del_1",
            packageName = "com.test.batchdel",
            appName = "BatchDel",
            title = "Item 1",
            content = "Content 1",
            isRead = false
        )
        val notif2 = NotificationEntity(
            key = "batch_del_2",
            packageName = "com.test.batchdel",
            appName = "BatchDel",
            title = "Item 2",
            content = "Content 2",
            isRead = false
        )
        dao.insertNotifications(listOf(notif1, notif2))

        assertNotNull(dao.getNotificationByKey("batch_del_1"))
        assertNotNull(dao.getNotificationByKey("batch_del_2"))

        dao.markAsReadBatch(listOf("batch_del_1", "batch_del_2"), autoDeleteOnRead = true)

        assertNull(dao.getNotificationByKey("batch_del_1"))
        assertNull(dao.getNotificationByKey("batch_del_2"))
    }

    @Test
    fun markAllAsRead_withAutoDeleteOnRead_deletesAllUnreadRowsAndPreservesReadRows() = runBlocking {
        val unread1 = NotificationEntity(
            key = "mark_all_unread_1",
            packageName = "com.test.del",
            appName = "DelApp",
            title = "Unread 1",
            content = "Text",
            isRead = false
        )
        val unread2 = NotificationEntity(
            key = "mark_all_unread_2",
            packageName = "com.test.del",
            appName = "DelApp",
            title = "Unread 2",
            content = "Text",
            isRead = false
        )
        val alreadyRead = NotificationEntity(
            key = "mark_all_read_kept",
            packageName = "com.test.del",
            appName = "DelApp",
            title = "Already Read",
            content = "Text",
            isRead = true
        )
        dao.insertNotifications(listOf(unread1, unread2, alreadyRead))

        dao.markAllAsRead(autoDeleteOnRead = true)

        assertNull(dao.getNotificationByKey("mark_all_unread_1"))
        assertNull(dao.getNotificationByKey("mark_all_unread_2"))
        assertNotNull(dao.getNotificationByKey("mark_all_read_kept"))
    }

    @Test
    fun deleteByKeys_removesOnlyMatchingKeys() = runBlocking {
        val n1 = NotificationEntity(key = "k_del_1", packageName = "p", appName = "A", title = "T1", content = "C1")
        val n2 = NotificationEntity(key = "k_del_2", packageName = "p", appName = "A", title = "T2", content = "C2")
        val n3 = NotificationEntity(key = "k_del_3", packageName = "p", appName = "A", title = "T3", content = "C3")
        dao.insertNotifications(listOf(n1, n2, n3))

        dao.deleteByKeys(listOf("k_del_1", "k_del_3"))

        assertNull(dao.getNotificationByKey("k_del_1"))
        assertNotNull(dao.getNotificationByKey("k_del_2"))
        assertNull(dao.getNotificationByKey("k_del_3"))
    }

    @Test
    fun getRecentNotificationsList_returnsOrderedByPostTime() = runBlocking {
        val now = System.currentTimeMillis()
        val oldest = NotificationEntity(
            key = "recent_old",
            packageName = "p",
            appName = "A",
            title = "Old",
            content = "Old",
            postTime = now - 10000
        )
        val middle = NotificationEntity(
            key = "recent_mid",
            packageName = "p",
            appName = "A",
            title = "Mid",
            content = "Mid",
            postTime = now - 5000
        )
        val newest = NotificationEntity(
            key = "recent_new",
            packageName = "p",
            appName = "A",
            title = "New",
            content = "New",
            postTime = now
        )
        dao.insertNotifications(listOf(oldest, middle, newest))

        val recent = dao.getRecentNotificationsList(limit = 2)
        assertEquals(2, recent.size)
        assertEquals("recent_new", recent[0].key)
        assertEquals("recent_mid", recent[1].key)
    }

    @Test
    fun syncOperations_lifecycle_getUnsynced_updateStatus_and_purgePendingDeletes() = runBlocking {
        val entity = NotificationEntity(
            key = "sync_lifecycle_key",
            packageName = "com.test.sync",
            appName = "SyncApp",
            title = "Sync Title",
            content = "Sync Content",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        dao.insert(entity)

        val unsyncedBefore = dao.getUnsyncedNotifications()
        assertTrue(unsyncedBefore.any { it.key == "sync_lifecycle_key" })

        dao.updateSyncStatus("sync_lifecycle_key", SyncStatus.SYNCED)
        val unsyncedAfter = dao.getUnsyncedNotifications()
        assertFalse(unsyncedAfter.any { it.key == "sync_lifecycle_key" })
        assertEquals(SyncStatus.SYNCED, dao.getNotificationByKey("sync_lifecycle_key")?.syncStatus)

        dao.markPendingDelete("sync_lifecycle_key")
        assertEquals(SyncStatus.PENDING_DELETE, dao.getNotificationByKey("sync_lifecycle_key")?.syncStatus)

        dao.purgePendingDeletes()
        assertNull(dao.getNotificationByKey("sync_lifecycle_key"))
    }

    @Test
    fun syncOperations_updateSyncStatusBatch_updatesMultipleKeys() = runBlocking {
        val n1 = NotificationEntity(
            key = "batch_s_1",
            packageName = "p",
            appName = "A",
            title = "T1",
            content = "C1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        val n2 = NotificationEntity(
            key = "batch_s_2",
            packageName = "p",
            appName = "A",
            title = "T2",
            content = "C2",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        dao.insertNotifications(listOf(n1, n2))

        assertEquals(2, dao.getUnsyncedNotifications().filter { it.key.startsWith("batch_s_") }.size)

        dao.updateSyncStatusBatch(listOf("batch_s_1", "batch_s_2"), SyncStatus.SYNCED)

        assertEquals(0, dao.getUnsyncedNotifications().filter { it.key.startsWith("batch_s_") }.size)
        assertEquals(SyncStatus.SYNCED, dao.getNotificationByKey("batch_s_1")?.syncStatus)
        assertEquals(SyncStatus.SYNCED, dao.getNotificationByKey("batch_s_2")?.syncStatus)
    }

    @Test
    fun embeddingOperations_updateEmbeddingsBatch_and_getNeedingVectorization() = runBlocking {
        val withoutEmbedding = NotificationEntity(
            key = "vec_test_key",
            packageName = "p",
            appName = "A",
            title = "Needs Embedding",
            content = "Content",
            embedding = null
        )
        val id = dao.insert(withoutEmbedding)

        val needingVectorization = dao.getNotificationsNeedingVectorization()
        assertTrue(needingVectorization.any { it.id == id })

        val sampleEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val rawBytes = Converters().fromFloatArray(sampleEmbedding)!!
        dao.updateEmbeddingsBatch(listOf(id to rawBytes))

        val needingVectorizationAfter = dao.getNotificationsNeedingVectorization()
        assertFalse(needingVectorizationAfter.any { it.id == id })

        val updatedEntity = dao.getNotificationById(id)
        assertNotNull(updatedEntity)
        assertNotNull(updatedEntity?.embedding)
        assertEquals(4, updatedEntity?.embedding?.size)
        assertEquals(0.1f, updatedEntity?.embedding?.get(0) ?: 0f, 0.001f)
    }

    @Test
    fun dismissedAndTotalCountFlow_emitsAccurateCounts() = runBlocking {
        val nActive1 = NotificationEntity(
            key = "count_act_1",
            packageName = "p",
            appName = "A",
            title = "T1",
            content = "C1",
            isDismissed = false
        )
        val nActive2 = NotificationEntity(
            key = "count_act_2",
            packageName = "p",
            appName = "A",
            title = "T2",
            content = "C2",
            isDismissed = false
        )
        val nDismissed = NotificationEntity(
            key = "count_dis_1",
            packageName = "p",
            appName = "A",
            title = "T3",
            content = "C3",
            isDismissed = true
        )
        dao.insertNotifications(listOf(nActive1, nActive2, nDismissed))

        assertEquals(3, dao.getTotalNotificationCountFlow().first())
        assertEquals(1, dao.getDismissedNotificationCountFlow().first())

        dao.markDismissed("count_act_1")

        assertEquals(3, dao.getTotalNotificationCountFlow().first())
        assertEquals(2, dao.getDismissedNotificationCountFlow().first())
    }
}
