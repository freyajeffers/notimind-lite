package com.jeffers.notimindlite.data

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
class NotificationDaoFeaturesTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testActiveNotifications_ongoingPinnedToTop() = runBlocking {
        val regularNotif = NotificationEntity(
            key = "key1",
            packageName = "com.test.app",
            appName = "App 1",
            title = "Regular Notification",
            content = "Hello",
            postTime = 1000L,
            isOngoing = false,
            isDismissed = false
        )
        val ongoingNotif = NotificationEntity(
            key = "key2",
            packageName = "com.test.app2",
            appName = "App 2",
            title = "Ongoing Notification",
            content = "Music Playing",
            postTime = 500L,
            isOngoing = true,
            isDismissed = false
        )

        dao.insertNotification(regularNotif)
        dao.insertNotification(ongoingNotif)

        val activeList = dao.getActiveNotificationsFlow().first()
        assertEquals(2, activeList.size)
        // Ongoing notification must be first despite having an earlier postTime
        assertEquals("key2", activeList[0].key)
        assertTrue(activeList[0].isOngoing)
        assertEquals("key1", activeList[1].key)
    }

    @Test
    fun testSnoozedNotificationClassification_inRecentlyDismissed_notInLost() = runBlocking {
        val snoozedNotif = NotificationEntity(
            key = "snoozed1",
            packageName = "com.test.app",
            appName = "App",
            title = "Snoozed Item",
            content = "See you later",
            postTime = 1000L,
            isDismissed = true,
            dismissReason = 12, // REASON_SNOOZED
            dismissTime = 1500L
        )
        val lostNotif = NotificationEntity(
            key = "lost1",
            packageName = "com.test.app",
            appName = "App",
            title = "Lost Item",
            content = "Crashed/Unknown",
            postTime = 1000L,
            isDismissed = true,
            dismissReason = 999, // Unclassified reason
            dismissTime = 1600L
        )

        dao.insertNotification(snoozedNotif)
        dao.insertNotification(lostNotif)

        val recentlyDismissed = dao.getRecentlyDismissedFlow().first()
        val lostList = dao.getLostNotificationsFlow().first()

        assertTrue("Snoozed notification must be present in recently dismissed", recentlyDismissed.any { it.key == "snoozed1" })
        assertFalse("Snoozed notification must NOT be present in lost", lostList.any { it.key == "snoozed1" })
        assertTrue("Lost notification must be present in lost list", lostList.any { it.key == "lost1" })
    }

    @Test
    fun testFilteredNotifications_containsSystemAndClutter() = runBlocking {
        val sysNotif = NotificationEntity(
            key = "sys1",
            packageName = "android",
            appName = "Android System",
            title = "USB Debugging",
            content = "Connected",
            postTime = 2000L,
            category = "sys",
            priority = -1
        )
        val userNotif = NotificationEntity(
            key = "user1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Chat",
            content = "Hey",
            postTime = 2500L,
            category = "msg",
            priority = 1
        )

        dao.insertNotification(sysNotif)
        dao.insertNotification(userNotif)

        val filtered = dao.getFilteredNotificationsFlow().first()
        assertEquals(1, filtered.size)
        assertEquals("sys1", filtered[0].key)
    }

    @Test
    fun testMarkDismissedWithReasonByMatching_removesFromActive() = runBlocking {
        val notif = NotificationEntity(
            key = "msg_key",
            packageName = "com.telegram.messenger",
            appName = "Telegram",
            title = "New Message",
            content = "Check this out",
            postTime = 3000L,
            isDismissed = false
        )
        dao.insertNotification(notif)

        var active = dao.getActiveNotificationsFlow().first()
        assertEquals(1, active.size)

        dao.markDismissedWithReasonByMatching(
            key = "msg_key",
            packageName = "com.telegram.messenger",
            title = "New Message",
            content = "Check this out",
            reason = 1,
            dismissTime = 3500L
        )

        active = dao.getActiveNotificationsFlow().first()
        assertTrue("Active notifications list must be empty after dismissal", active.isEmpty())

        val dismissed = dao.getRecentlyDismissedFlow().first()
        assertEquals(1, dismissed.size)
        assertEquals("msg_key", dismissed[0].key)
    }

    @Test
    fun testRecentlyDismissed_sortedByTimeDismissed() = runBlocking {
        val notif1 = NotificationEntity(
            key = "notif1",
            packageName = "com.test.app1",
            appName = "App 1",
            title = "First Received",
            content = "Old post, recently dismissed",
            postTime = 1000L,
            isDismissed = true,
            dismissReason = 1,
            dismissTime = 5000L
        )
        val notif2 = NotificationEntity(
            key = "notif2",
            packageName = "com.test.app2",
            appName = "App 2",
            title = "Later Received",
            content = "Newer post, earlier dismissed",
            postTime = 3000L,
            isDismissed = true,
            dismissReason = 1,
            dismissTime = 4000L
        )

        dao.insertNotification(notif1)
        dao.insertNotification(notif2)

        val recentlyDismissed = dao.getRecentlyDismissedFlow().first()
        assertEquals(2, recentlyDismissed.size)
        // notif1 was dismissed at 5000L, so it must appear before notif2 (dismissed at 4000L)
        assertEquals("notif1", recentlyDismissed[0].key)
        assertEquals("notif2", recentlyDismissed[1].key)
    }
}
