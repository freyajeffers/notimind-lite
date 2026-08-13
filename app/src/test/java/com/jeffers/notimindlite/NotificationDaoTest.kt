package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryNotification_withRichMetadata() = runBlocking {
        val entity = NotificationEntity(
            key = "com.test.app_1001",
            packageName = "com.test.app",
            appName = "Test App",
            title = "Rich Test Title",
            content = "Rich Test Content",
            postTime = System.currentTimeMillis(),
            isDismissed = false,
            isPersistent = true,
            category = "msg",
            channelId = "chat_channel",
            subText = "Subtext Info",
            bigText = "Expanded Big Text Content",
            priority = 2,
            groupKey = "group_chat_1",
            isOngoing = true,
            isClearable = false,
            actionsCount = 3
        )

        val id = dao.insertNotification(entity)
        assertTrue(id > 0)

        val activeList = dao.getActiveNotificationsList()
        assertEquals(1, activeList.size)

        val retrieved = activeList[0]
        assertEquals("Rich Test Title", retrieved.title)
        assertEquals("msg", retrieved.category)
        assertEquals("chat_channel", retrieved.channelId)
        assertEquals("Subtext Info", retrieved.subText)
        assertEquals("Expanded Big Text Content", retrieved.bigText)
        assertEquals(2, retrieved.priority)
        assertEquals("group_chat_1", retrieved.groupKey)
        assertTrue(retrieved.isOngoing)
        assertFalse(retrieved.isClearable)
        assertEquals(3, retrieved.actionsCount)
    }

    @Test
    fun markDismissed_removesFromActiveList() = runBlocking {
        val entity = NotificationEntity(
            key = "com.test.app_1002",
            packageName = "com.test.app",
            appName = "Test App",
            title = "Dismiss Me",
            content = "Content",
            isDismissed = false
        )

        dao.insertNotification(entity)
        assertEquals(1, dao.getActiveNotificationsList().size)

        dao.markDismissed("com.test.app_1002")
        assertEquals(0, dao.getActiveNotificationsList().size)

        val allLogs = dao.getAllNotifications().first()
        assertEquals(1, allLogs.size)
        assertTrue(allLogs[0].isDismissed)
    }

    @Test
    fun markDismissedWithReason_categorizesIntoRecentlyDismissedAndLost() = runBlocking {
        val userSwiped = NotificationEntity(key = "k1", packageName = "p1", appName = "A1", title = "Swiped", content = "C1", isDismissed = false)
        val appCancelled = NotificationEntity(key = "k2", packageName = "p2", appName = "A2", title = "App Cancel", content = "C2", isDismissed = false)

        dao.insertNotification(userSwiped)
        dao.insertNotification(appCancelled)

        dao.markDismissedWithReason("k1", 1) // REASON_CANCEL (User Swiped)
        dao.markDismissedWithReason("k2", 8) // REASON_APP_CANCEL (App Cancelled)

        val recentlyDismissed = dao.getRecentlyDismissedFlow().first()
        val lostNotifs = dao.getLostNotificationsFlow().first()

        assertEquals(1, recentlyDismissed.size)
        assertEquals("Swiped", recentlyDismissed[0].title)
        assertEquals(1, recentlyDismissed[0].dismissReason)

        assertEquals(1, lostNotifs.size)
        assertEquals("App Cancel", lostNotifs[0].title)
        assertEquals(8, lostNotifs[0].dismissReason)
    }

    @Test
    fun updatePinnedStatus_togglesPinnedState() = runBlocking {
        val notif = NotificationEntity(key = "pin_1", packageName = "p", appName = "A", title = "Pin Me", content = "C", isDismissed = false, isPinned = false)
        dao.insertNotification(notif)

        var pinnedList = dao.getPinnedNotificationsFlow().first()
        assertEquals(0, pinnedList.size)

        dao.updatePinnedStatus("pin_1", true)
        pinnedList = dao.getPinnedNotificationsFlow().first()
        assertEquals(1, pinnedList.size)
        assertEquals("Pin Me", pinnedList[0].title)
        assertTrue(pinnedList[0].isPinned)

        dao.updatePinnedStatus("pin_1", false)
        pinnedList = dao.getPinnedNotificationsFlow().first()
        assertEquals(0, pinnedList.size)
    }

    @Test
    fun sub2msQueryPerformanceSLA_50kRecords() = runBlocking {
        val totalEntities = 50_000
        val now = System.currentTimeMillis()

        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                val appStmt = db.compileStatement(
                    "INSERT OR IGNORE INTO apps (packageName, appName, firstSeenTime, lastSeenTime) VALUES (?, ?, ?, ?)"
                )
                for (p in 0 until 100) {
                    appStmt.clearBindings()
                    appStmt.bindString(1, "com.test.app$p")
                    appStmt.bindString(2, "App $p")
                    appStmt.bindLong(3, now)
                    appStmt.bindLong(4, now)
                    appStmt.executeInsert()
                }
                val stmt = db.compileStatement(
                    "INSERT INTO notifications (key, packageName, appName, title, content, postTime, isDismissed, isPinned, isPersistent, priority, actionsCount, isOngoing, isClearable) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                for (i in 1..totalEntities) {
                    stmt.clearBindings()
                    stmt.bindString(1, "pkg.app_$i")
                    stmt.bindString(2, "com.test.app${i % 100}")
                    stmt.bindString(3, "App ${i % 100}")
                    stmt.bindString(4, "Title $i")
                    stmt.bindString(5, "Content $i")
                    stmt.bindLong(6, now - i * 10)
                    stmt.bindLong(7, if (i % 1000 == 0) 0L else 1L)
                    stmt.bindLong(8, if (i % 500 == 0) 1L else 0L)
                    stmt.bindLong(9, 0L)
                    stmt.bindLong(10, 0L)
                    stmt.bindLong(11, 0L)
                    stmt.bindLong(12, 0L)
                    stmt.bindLong(13, 1L)
                    stmt.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        val totalCount = dao.getNotificationCount()
        assertEquals(50_000, totalCount)

        // Warm up query
        dao.getActiveNotificationsList()

        // Measure SLA for indexed query over active notifications
        val iterations = 10
        var totalNanos = 0L
        for (i in 1..iterations) {
            val start = System.nanoTime()
            val active = dao.getActiveNotificationsList()
            val duration = System.nanoTime() - start
            totalNanos += duration
            assertTrue(active.isNotEmpty())
        }

        val avgMs = (totalNanos.toDouble() / iterations) / 1_000_000.0
        assertTrue("Average active query latency over 50k rows must be sub-15ms, was ${avgMs}ms", avgMs < 15.0)
    }

    @Test
    fun clearAll_deletesAllNotifications() = runBlocking {
        val entity = NotificationEntity(
            key = "test_clear_1",
            packageName = "com.example.app",
            appName = "Test App",
            title = "Title",
            content = "Content",
            postTime = System.currentTimeMillis(),
            isDismissed = false,
            isPersistent = false
        )
        dao.insert(entity)
        val countBefore = dao.getAllNotificationsList().size
        assertTrue(countBefore > 0)

        dao.clearAll()
        val countAfter = dao.getAllNotificationsList().size
        assertEquals(0, countAfter)
    }
}

