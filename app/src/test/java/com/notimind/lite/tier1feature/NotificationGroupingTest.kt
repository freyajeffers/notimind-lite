package com.notimind.lite.tier1feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.ui.components.groupNotifications
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationGroupingTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGroupNotifications_aggregatesByGroupKeyAndSortsByLatest() {
        val now = System.currentTimeMillis()
        val notif1 = NotificationEntity(
            key = "msg_1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            content = "Hey there!",
            postTime = now - 10000,
            groupKey = "chat_alice"
        )
        val notif2 = NotificationEntity(
            key = "msg_2",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            content = "Are you available?",
            postTime = now - 2000,
            groupKey = "chat_alice"
        )
        val notif3 = NotificationEntity(
            key = "mail_1",
            packageName = "com.google.android.gm",
            appName = "Gmail",
            title = "Security Alert",
            content = "New sign-in detected",
            postTime = now - 5000,
            groupKey = "gmail_alerts"
        )

        val groups = groupNotifications(listOf(notif1, notif2, notif3))

        assertEquals(2, groups.size)
        // chat_alice latest is (now - 2000), gmail is (now - 5000) -> chat_alice must be first
        assertEquals("chat_alice", groups[0].groupKey)
        assertEquals(2, groups[0].items.size)
        assertEquals("Are you available?", groups[0].items[0].content)

        assertEquals("gmail_alerts", groups[1].groupKey)
        assertEquals(1, groups[1].items.size)
    }

    @Test
    fun testGroupNotifications_pinnedGroupsAppearFirst() {
        val now = System.currentTimeMillis()
        val unpinnedNew = NotificationEntity(
            key = "unpinned_1",
            packageName = "com.news",
            appName = "News",
            title = "Breaking",
            content = "Latest news",
            postTime = now,
            groupKey = "news_group",
            isPinned = false
        )
        val pinnedOld = NotificationEntity(
            key = "pinned_1",
            packageName = "com.todo",
            appName = "Todo",
            title = "Urgent Task",
            content = "Submit report",
            postTime = now - 50000,
            groupKey = "todo_group",
            isPinned = true
        )

        val groups = groupNotifications(listOf(unpinnedNew, pinnedOld))

        assertEquals(2, groups.size)
        assertEquals("todo_group", groups[0].groupKey)
        assertTrue(groups[0].isPinned)
        assertEquals("news_group", groups[1].groupKey)
        assertFalse(groups[1].isPinned)
    }

    @Test
    fun testDao_insertAndQueryNotificationGroups() = runBlocking {
        val now = System.currentTimeMillis()
        val notif1 = NotificationEntity(
            key = "n1",
            packageName = "com.slack",
            appName = "Slack",
            title = "#general",
            content = "Meeting at 3pm",
            postTime = now - 1000,
            groupKey = "slack_general"
        )
        val notif2 = NotificationEntity(
            key = "n2",
            packageName = "com.slack",
            appName = "Slack",
            title = "#general",
            content = "Meeting link posted",
            postTime = now,
            groupKey = "slack_general"
        )

        dao.insertNotifications(listOf(notif1, notif2))

        val groups = dao.getAllGroupsWithChildrenFlow().first()
        assertEquals(1, groups.size)
        assertEquals("slack_general", groups[0].group.groupKey)
        assertEquals(2, groups[0].notifications.size)

        val appWithRelations = dao.getAppWithGroupsAndNotifications("com.slack").first()
        assertNotNull(appWithRelations)
        assertEquals("com.slack", appWithRelations?.app?.packageName)
        assertEquals(1, appWithRelations?.groups?.size)
        assertEquals(2, appWithRelations?.notifications?.size)

        val notifWithRelations = dao.getNotificationWithGroupAndApp("n1").first()
        assertNotNull(notifWithRelations)
        assertEquals("n1", notifWithRelations?.notification?.key)
        assertEquals("slack_general", notifWithRelations?.group?.groupKey)
        assertEquals("com.slack", notifWithRelations?.app?.packageName)
    }
}
