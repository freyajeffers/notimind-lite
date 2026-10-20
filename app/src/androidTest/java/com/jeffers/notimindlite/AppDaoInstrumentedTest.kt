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
}
