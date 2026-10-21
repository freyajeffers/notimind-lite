package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDao
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.AppEntity
import com.jeffers.notimindlite.data.local.BackupDao
import com.jeffers.notimindlite.data.local.BackupRecord
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.NotificationQueryDao
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
class AppDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var backupDao: BackupDao
    private lateinit var queryDao: NotificationQueryDao
    private lateinit var notificationDao: NotificationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        backupDao = database.backupDao()
        queryDao = database.notificationQueryDao()
        notificationDao = database.notificationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertOrUpdateApp_and_getAppByPackage_storesAndRetrievesCorrectly() = runBlocking {
        val app = AppEntity(
            packageName = "com.chat.mobile",
            appName = "Chat Mobile",
            appIconUri = "content://icons/chat",
            syncStatus = SyncStatus.SYNCED
        )
        appDao.insertOrUpdateApp(app)

        val retrieved = appDao.getAppByPackage("com.chat.mobile")
        assertNotNull(retrieved)
        assertEquals("Chat Mobile", retrieved?.appName)
        assertEquals(SyncStatus.SYNCED, retrieved?.syncStatus)

        val updated = app.copy(appName = "Chat Mobile Pro")
        appDao.insertOrUpdateApp(updated)

        val updatedRetrieved = appDao.getAppByPackage("com.chat.mobile")
        assertEquals("Chat Mobile Pro", updatedRetrieved?.appName)
    }

    @Test
    fun getAllAppsFlow_emitsSortedByAppName() = runBlocking {
        val appZ = AppEntity(packageName = "com.z.app", appName = "Zeta App")
        val appA = AppEntity(packageName = "com.a.app", appName = "Alpha App")
        val appM = AppEntity(packageName = "com.m.app", appName = "Mid App")

        appDao.insertOrUpdateApp(appZ)
        appDao.insertOrUpdateApp(appA)
        appDao.insertOrUpdateApp(appM)

        val allApps = appDao.getAllAppsFlow().first()
        assertEquals(3, allApps.size)
        assertEquals("Alpha App", allApps[0].appName)
        assertEquals("Mid App", allApps[1].appName)
        assertEquals("Zeta App", allApps[2].appName)
    }

    @Test
    fun getAppWithNotifications_and_cascadeDeleteApp() = runBlocking {
        val app = AppEntity(packageName = "com.notes.secure", appName = "SecureNotes")
        appDao.insertOrUpdateApp(app)

        val notif1 = NotificationEntity(
            key = "note_1",
            packageName = "com.notes.secure",
            appName = "SecureNotes",
            title = "Reminder 1",
            content = "Doctor appointment"
        )
        val notif2 = NotificationEntity(
            key = "note_2",
            packageName = "com.notes.secure",
            appName = "SecureNotes",
            title = "Reminder 2",
            content = "Groceries list"
        )
        notificationDao.insertNotifications(listOf(notif1, notif2))

        val appWithNotes = appDao.getAppWithNotifications("com.notes.secure")
        assertNotNull(appWithNotes)
        assertEquals("com.notes.secure", appWithNotes?.app?.packageName)
        assertEquals(2, appWithNotes?.notifications?.size)

        appDao.deleteApp("com.notes.secure")
        assertNull(appDao.getAppByPackage("com.notes.secure"))
        val remainingNotifications = notificationDao.getAllNotificationsList()
        assertTrue(remainingNotifications.isEmpty())
    }

    @Test
    fun backupDao_operations_insertQueryClear() = runBlocking {
        val record1 = BackupRecord(
            actionType = "EXPORT",
            fileHash = "sha256_hash_1",
            signature = "sig_bytes_1",
            fileName = "backup_2026.json"
        )
        val record2 = BackupRecord(
            actionType = "IMPORT",
            fileHash = "sha256_hash_2",
            signature = "sig_bytes_2",
            fileName = "backup_restored.json"
        )

        backupDao.insertRecord(record1)
        backupDao.insertRecord(record2)

        val retrieved = backupDao.getRecordByHash("sha256_hash_1")
        assertNotNull(retrieved)
        assertEquals("EXPORT", retrieved?.actionType)
        assertEquals("backup_2026.json", retrieved?.fileName)

        val allRecords = backupDao.getAllRecords().first()
        assertEquals(2, allRecords.size)

        backupDao.clearRecords()
        val emptyRecords = backupDao.getAllRecords().first()
        assertTrue(emptyRecords.isEmpty())
    }

    @Test
    fun notificationQueryDao_countsAndFlows_reflectAccurateState() = runBlocking {
        val activeNotif = NotificationEntity(
            key = "active_1",
            packageName = "com.email.app",
            appName = "Email",
            title = "Inbox",
            content = "New email received",
            isDismissed = false,
            isRead = false
        )
        val dismissedNotif = NotificationEntity(
            key = "dismissed_1",
            packageName = "com.email.app",
            appName = "Email",
            title = "Newsletter",
            content = "Weekly digest",
            isDismissed = true,
            isRead = true
        )
        notificationDao.insertNotifications(listOf(activeNotif, dismissedNotif))

        assertEquals(2, queryDao.getNotificationCount())
        assertEquals(1, queryDao.getDismissedNotificationCountFlow().first())
        assertEquals(2, queryDao.getTotalNotificationCountFlow().first())
        assertEquals(1, queryDao.getUnreadCountFlow().first())
    }

    @Test
    fun getAllAppsWithNotificationsFlow_emitsAlphabeticallyWithCorrectChildCounts() = runBlocking {
        val appB = AppEntity(
            packageName = "com.beta.app",
            appName = "Beta App"
        )
        val appA = AppEntity(
            packageName = "com.alpha.app",
            appName = "Alpha App"
        )
        appDao.insertOrUpdateApp(appB)
        appDao.insertOrUpdateApp(appA)

        val notifA1 = NotificationEntity(
            key = "alpha_1",
            packageName = "com.alpha.app",
            appName = "Alpha App",
            title = "A1",
            content = "C1"
        )
        val notifA2 = NotificationEntity(
            key = "alpha_2",
            packageName = "com.alpha.app",
            appName = "Alpha App",
            title = "A2",
            content = "C2"
        )
        val notifB1 = NotificationEntity(
            key = "beta_1",
            packageName = "com.beta.app",
            appName = "Beta App",
            title = "B1",
            content = "C1"
        )
        notificationDao.insertNotifications(listOf(notifA1, notifA2, notifB1))

        val appsWithNotifications = appDao.getAllAppsWithNotificationsFlow().first()
        assertEquals(2, appsWithNotifications.size)

        // Alphabetical by appName: Alpha App first, then Beta App
        assertEquals("Alpha App", appsWithNotifications[0].app.appName)
        assertEquals(2, appsWithNotifications[0].notifications.size)

        assertEquals("Beta App", appsWithNotifications[1].app.appName)
        assertEquals(1, appsWithNotifications[1].notifications.size)
    }

    @Test
    fun notificationQueryDao_markAsRead_and_autoDeleteOnRead() = runBlocking {
        val notif1 = NotificationEntity(
            key = "query_read_1",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Test 1",
            content = "Unread message 1",
            isRead = false
        )
        val notif2 = NotificationEntity(
            key = "query_read_2",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Test 2",
            content = "Unread message 2",
            isRead = false
        )
        notificationDao.insertNotifications(listOf(notif1, notif2))
        assertEquals(2, queryDao.getUnreadCountFlow().first())

        // Mark notif1 as read with autoDeleteOnRead = false
        queryDao.markAsRead("query_read_1", autoDeleteOnRead = false)
        assertEquals(1, queryDao.getUnreadCountFlow().first())
        val fetched1 = notificationDao.getNotificationByKey("query_read_1")
        assertNotNull(fetched1)
        assertTrue(fetched1?.isRead == true)

        // Mark notif2 as read with autoDeleteOnRead = true -> should be deleted
        queryDao.markAsRead("query_read_2", autoDeleteOnRead = true)
        assertEquals(0, queryDao.getUnreadCountFlow().first())
        val fetched2 = notificationDao.getNotificationByKey("query_read_2")
        assertNull(fetched2)
    }

    @Test
    fun notificationQueryDao_markAllAsRead_and_autoDeleteOnRead() = runBlocking {
        val u1 = NotificationEntity(
            key = "all_read_1",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Unread 1",
            content = "Content",
            isRead = false
        )
        val u2 = NotificationEntity(
            key = "all_read_2",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Unread 2",
            content = "Content",
            isRead = false
        )
        notificationDao.insertNotifications(listOf(u1, u2))
        assertEquals(2, queryDao.getUnreadCountFlow().first())

        // Mark all as read without deleting
        queryDao.markAllAsRead(autoDeleteOnRead = false)
        assertEquals(0, queryDao.getUnreadCountFlow().first())
        assertEquals(2, queryDao.getNotificationCount())

        // Insert new unread notifications
        val u3 = NotificationEntity(
            key = "all_read_3",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Unread 3",
            content = "Content",
            isRead = false
        )
        val u4 = NotificationEntity(
            key = "all_read_4",
            packageName = "com.test.app",
            appName = "TestApp",
            title = "Unread 4",
            content = "Content",
            isRead = false
        )
        notificationDao.insertNotifications(listOf(u3, u4))
        assertEquals(2, queryDao.getUnreadCountFlow().first())
        assertEquals(4, queryDao.getNotificationCount())

        // Mark all as read WITH autoDeleteOnRead = true -> should delete only unread ones (u3, u4)
        queryDao.markAllAsRead(autoDeleteOnRead = true)
        assertEquals(0, queryDao.getUnreadCountFlow().first())
        assertEquals(2, queryDao.getNotificationCount())
        assertNotNull(notificationDao.getNotificationByKey("all_read_1"))
        assertNotNull(notificationDao.getNotificationByKey("all_read_2"))
        assertNull(notificationDao.getNotificationByKey("all_read_3"))
        assertNull(notificationDao.getNotificationByKey("all_read_4"))
    }

    @Test
    fun notificationQueryDao_markAsReadBatch_and_autoDelete() = runBlocking {
        val b1 = NotificationEntity(
            key = "batch_1",
            packageName = "com.pkg",
            appName = "App",
            title = "T1",
            content = "C1",
            isRead = false
        )
        val b2 = NotificationEntity(
            key = "batch_2",
            packageName = "com.pkg",
            appName = "App",
            title = "T2",
            content = "C2",
            isRead = false
        )
        val b3 = NotificationEntity(
            key = "batch_3",
            packageName = "com.pkg",
            appName = "App",
            title = "T3",
            content = "C3",
            isRead = false
        )
        notificationDao.insertNotifications(listOf(b1, b2, b3))

        // Empty batch no-op
        queryDao.markAsReadBatch(emptyList(), autoDeleteOnRead = false)
        assertEquals(3, queryDao.getUnreadCountFlow().first())

        // Mark batch as read
        queryDao.markAsReadBatch(listOf("batch_1", "batch_2"), autoDeleteOnRead = false)
        assertEquals(1, queryDao.getUnreadCountFlow().first())
        assertTrue(notificationDao.getNotificationByKey("batch_1")?.isRead == true)
        assertTrue(notificationDao.getNotificationByKey("batch_2")?.isRead == true)
        assertFalse(notificationDao.getNotificationByKey("batch_3")?.isRead == true)

        // Delete batch using autoDeleteOnRead = true
        queryDao.markAsReadBatch(listOf("batch_2", "batch_3"), autoDeleteOnRead = true)
        assertNotNull(notificationDao.getNotificationByKey("batch_1"))
        assertNull(notificationDao.getNotificationByKey("batch_2"))
        assertNull(notificationDao.getNotificationByKey("batch_3"))
    }

    @Test
    fun notificationQueryDao_updatePinnedStatus_singleAndBatch() = runBlocking {
        val p1 = NotificationEntity(
            key = "pin_1",
            packageName = "com.pkg",
            appName = "App",
            title = "P1",
            content = "C1",
            isPinned = false
        )
        val p2 = NotificationEntity(
            key = "pin_2",
            packageName = "com.pkg",
            appName = "App",
            title = "P2",
            content = "C2",
            isPinned = false
        )
        val p3 = NotificationEntity(
            key = "pin_3",
            packageName = "com.pkg",
            appName = "App",
            title = "P3",
            content = "C3",
            isPinned = false
        )
        notificationDao.insertNotifications(listOf(p1, p2, p3))

        // Pin single
        queryDao.updatePinnedStatus("pin_1", true)
        val pinnedAfterSingle = queryDao.getPinnedNotificationsFlow().first()
        assertEquals(1, pinnedAfterSingle.size)
        assertEquals("pin_1", pinnedAfterSingle[0].key)

        // Pin batch
        queryDao.updatePinnedStatusBatch(listOf("pin_2", "pin_3"), true)
        val pinnedAfterBatch = queryDao.getPinnedNotificationsFlow().first()
        assertEquals(3, pinnedAfterBatch.size)

        // Unpin single
        queryDao.updatePinnedStatus("pin_1", false)
        val pinnedAfterUnpinSingle = queryDao.getPinnedNotificationsFlow().first()
        assertEquals(2, pinnedAfterUnpinSingle.size)
        assertFalse(pinnedAfterUnpinSingle.any { it.key == "pin_1" })

        // Unpin batch
        queryDao.updatePinnedStatusBatch(listOf("pin_2", "pin_3"), false)
        val pinnedEmpty = queryDao.getPinnedNotificationsFlow().first()
        assertTrue(pinnedEmpty.isEmpty())
    }
}
