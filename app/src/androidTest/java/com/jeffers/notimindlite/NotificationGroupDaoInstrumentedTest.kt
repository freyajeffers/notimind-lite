package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDao
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.AppEntity
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.NotificationGroupDao
import com.jeffers.notimindlite.data.local.NotificationGroupDetailDao
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
class NotificationGroupDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var notificationDao: NotificationDao
    private lateinit var groupDao: NotificationGroupDao
    private lateinit var groupDetailDao: NotificationGroupDetailDao
    private lateinit var appDao: AppDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notificationDao = database.notificationDao()
        groupDao = database.notificationGroupDao()
        groupDetailDao = database.notificationGroupDetailDao()
        appDao = database.appDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertNotifications_aggregatesGroupsWithChildrenCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        val notif1 = NotificationEntity(
            key = "msg_1",
            packageName = "com.slack",
            appName = "Slack",
            title = "#engineering",
            content = "Build passed",
            postTime = now - 5000,
            groupKey = "slack_eng"
        )
        val notif2 = NotificationEntity(
            key = "msg_2",
            packageName = "com.slack",
            appName = "Slack",
            title = "#engineering",
            content = "Release deployed",
            postTime = now - 1000,
            groupKey = "slack_eng"
        )
        notificationDao.insertNotifications(listOf(notif1, notif2))

        val groupsWithChildren = groupDao.getAllGroupsWithChildrenFlow().first()
        assertEquals(1, groupsWithChildren.size)

        val groupWithChild = groupsWithChildren[0]
        assertEquals("slack_eng", groupWithChild.group.groupKey)
        assertEquals("com.slack", groupWithChild.group.packageName)
        assertEquals(2, groupWithChild.notifications.size)
    }

    @Test
    fun activeAndDismissedGroupsFlow_filtersByDismissalState() = runBlocking {
        val now = System.currentTimeMillis()
        val activeNotif = NotificationEntity(
            key = "active_item",
            packageName = "com.task.manager",
            appName = "TaskManager",
            title = "Task Reminder",
            content = "Prepare slides",
            postTime = now,
            groupKey = "tasks_active",
            isDismissed = false
        )
        val dismissedNotif = NotificationEntity(
            key = "dismissed_item",
            packageName = "com.news.daily",
            appName = "DailyNews",
            title = "Morning Brief",
            content = "Top headlines",
            postTime = now - 10000,
            groupKey = "news_dismissed",
            isDismissed = true
        )
        notificationDao.insertNotifications(listOf(activeNotif, dismissedNotif))

        val activeGroups = groupDao.getActiveGroupsFlow().first()
        assertEquals(1, activeGroups.size)
        assertEquals("tasks_active", activeGroups[0].groupKey)
        assertFalse(activeGroups[0].isDismissed)

        val dismissedGroups = groupDao.getDismissedGroupsFlow().first()
        assertEquals(1, dismissedGroups.size)
        assertEquals("news_dismissed", dismissedGroups[0].groupKey)
        assertTrue(dismissedGroups[0].isDismissed)
    }

    @Test
    fun groupDetailDao_getGroupByKey_returnsExactMatchOrNull() = runBlocking {
        val notif = NotificationEntity(
            key = "query_key",
            packageName = "com.banking.secure",
            appName = "SecureBank",
            title = "Alert",
            content = "Statement Ready",
            groupKey = "bank_alerts"
        )
        notificationDao.insertNotifications(listOf(notif))

        val foundGroup = groupDetailDao.getGroupByKey("bank_alerts")
        assertNotNull(foundGroup)
        assertEquals("bank_alerts", foundGroup?.groupKey)
        assertEquals("com.banking.secure", foundGroup?.packageName)
        assertEquals("SecureBank", foundGroup?.appName)

        val missingGroup = groupDetailDao.getGroupByKey("non_existent_key")
        assertNull(missingGroup)
    }

    @Test
    fun groupDetailDao_getNotificationsForGroup_orderedByPostTimeDesc() = runBlocking {
        val now = System.currentTimeMillis()
        val older = NotificationEntity(
            key = "item_old",
            packageName = "com.chat.team",
            appName = "TeamChat",
            title = "Channel",
            content = "Old message",
            postTime = now - 10000,
            groupKey = "chat_group"
        )
        val newer = NotificationEntity(
            key = "item_new",
            packageName = "com.chat.team",
            appName = "TeamChat",
            title = "Channel",
            content = "New message",
            postTime = now - 2000,
            groupKey = "chat_group"
        )
        notificationDao.insertNotifications(listOf(older, newer))

        val notifications = groupDetailDao.getNotificationsForGroup("chat_group")
        assertEquals(2, notifications.size)
        assertEquals("item_new", notifications[0].key)
        assertEquals("item_old", notifications[1].key)

        val flowNotifications = groupDetailDao.getNotificationsForGroupFlow("chat_group").first()
        assertEquals(2, flowNotifications.size)
        assertEquals("item_new", flowNotifications[0].key)
        assertEquals("item_old", flowNotifications[1].key)
    }

    @Test
    fun groupDao_pinnedGroups_orderedBeforeUnpinnedGroups() = runBlocking {
        val now = System.currentTimeMillis()
        val unpinnedRecent = NotificationEntity(
            key = "unpinned_item",
            packageName = "com.social.app",
            appName = "SocialApp",
            title = "Feed",
            content = "New post from friend",
            postTime = now,
            groupKey = "social_group",
            isPinned = false
        )
        val pinnedOlder = NotificationEntity(
            key = "pinned_item",
            packageName = "com.work.email",
            appName = "WorkEmail",
            title = "Important",
            content = "Board meeting memo",
            postTime = now - 60000,
            groupKey = "work_group",
            isPinned = true
        )
        notificationDao.insertNotifications(listOf(unpinnedRecent, pinnedOlder))

        val allGroups = groupDao.getAllGroupsFlow().first()
        assertEquals(2, allGroups.size)
        assertEquals("work_group", allGroups[0].groupKey)
        assertTrue(allGroups[0].isPinned)
        assertEquals("social_group", allGroups[1].groupKey)
        assertFalse(allGroups[1].isPinned)
    }

    @Test
    fun activeAndDismissedGroupsWithChildrenFlow_emitsFilteredGroupsAndChildren() = runBlocking {
        val now = System.currentTimeMillis()
        val activeNotif = NotificationEntity(
            key = "item_active_child",
            packageName = "com.calendar.app",
            appName = "Calendar",
            title = "Standup",
            content = "Starting now",
            postTime = now,
            groupKey = "cal_active",
            isDismissed = false
        )
        val dismissedNotif = NotificationEntity(
            key = "item_dismissed_child",
            packageName = "com.promo.app",
            appName = "Promo",
            title = "Discount",
            content = "50% off",
            postTime = now - 5000,
            groupKey = "promo_dismissed",
            isDismissed = true
        )
        notificationDao.insertNotifications(listOf(activeNotif, dismissedNotif))

        val activeGroupsWithChildren = groupDao.getActiveGroupsWithChildrenFlow().first()
        assertEquals(1, activeGroupsWithChildren.size)
        assertEquals("cal_active", activeGroupsWithChildren[0].group.groupKey)
        assertEquals(1, activeGroupsWithChildren[0].notifications.size)
        assertEquals("item_active_child", activeGroupsWithChildren[0].notifications[0].key)

        val dismissedGroupsWithChildren = groupDao.getDismissedGroupsWithChildrenFlow().first()
        assertEquals(1, dismissedGroupsWithChildren.size)
        assertEquals("promo_dismissed", dismissedGroupsWithChildren[0].group.groupKey)
        assertEquals(1, dismissedGroupsWithChildren[0].notifications.size)
        assertEquals("item_dismissed_child", dismissedGroupsWithChildren[0].notifications[0].key)
    }

    @Test
    fun getAppWithGroupsAndNotifications_emitsAssociatedRelations() = runBlocking {
        val now = System.currentTimeMillis()
        val app = AppEntity(
            packageName = "com.chat.team",
            appName = "TeamChat"
        )
        appDao.insertOrUpdateApp(app)

        val notif1 = NotificationEntity(
            key = "chat_rel_1",
            packageName = "com.chat.team",
            appName = "TeamChat",
            title = "Engineering",
            content = "Build success",
            postTime = now - 1000,
            groupKey = "team_eng"
        )
        val notif2 = NotificationEntity(
            key = "chat_rel_2",
            packageName = "com.chat.team",
            appName = "TeamChat",
            title = "General",
            content = "Lunch time",
            postTime = now,
            groupKey = "team_gen"
        )
        notificationDao.insertNotifications(listOf(notif1, notif2))

        val appWithRelations = groupDao.getAppWithGroupsAndNotifications("com.chat.team").first()
        assertNotNull(appWithRelations)
        assertEquals("TeamChat", appWithRelations?.app?.appName)
        assertEquals(2, appWithRelations?.groups?.size)
        assertEquals(2, appWithRelations?.notifications?.size)
    }

    @Test
    fun getNotificationWithGroupAndApp_emitsCombinedEntity() = runBlocking {
        val app = AppEntity(
            packageName = "com.banking.secure",
            appName = "SecureBank"
        )
        appDao.insertOrUpdateApp(app)

        val notif = NotificationEntity(
            key = "notif_combo_key",
            packageName = "com.banking.secure",
            appName = "SecureBank",
            title = "Deposit Received",
            content = "Salary arrived",
            groupKey = "bank_deposits"
        )
        notificationDao.insertNotifications(listOf(notif))

        val combined = groupDao.getNotificationWithGroupAndApp("notif_combo_key").first()
        assertNotNull(combined)
        assertEquals("notif_combo_key", combined?.notification?.key)
        assertEquals("bank_deposits", combined?.group?.groupKey)
        assertEquals("SecureBank", combined?.app?.appName)
    }
}
