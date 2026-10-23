package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.Converters
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.notificationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun instrumented_insertAndQueryNotification() = runBlocking {
        val entity = NotificationEntity(
            key = "inst_key_1",
            packageName = "com.inst.test",
            appName = "InstTestApp",
            title = "Instrumented Test Title",
            content = "Instrumented Content",
            category = "sys",
            channelId = "inst_channel",
            priority = 1
        )

        val id = dao.insertNotification(entity)
        assertTrue(id > 0)

        val activeList = dao.getActiveNotificationsList()
        assertEquals(1, activeList.size)
        assertEquals("Instrumented Test Title", activeList[0].title)
        assertEquals("sys", activeList[0].category)
        assertEquals("inst_channel", activeList[0].channelId)
    }

    @Test
    fun instrumented_batchInsertAndGetActiveList() = runBlocking {
        val list = listOf(
            NotificationEntity(
                key = "batch_1",
                packageName = "com.app.one",
                appName = "AppOne",
                title = "Order Placed",
                content = "Order #1234 confirmed",
                isDismissed = false
            ),
            NotificationEntity(
                key = "batch_2",
                packageName = "com.app.two",
                appName = "AppTwo",
                title = "Delivery Arrived",
                content = "Package at your front door",
                isDismissed = false
            )
        )
        val ids = dao.insertNotifications(list)
        assertEquals(2, ids.size)

        val active = dao.getActiveNotificationsList()
        assertTrue(active.any { it.key == "batch_1" })
        assertTrue(active.any { it.key == "batch_2" })
    }

    @Test
    fun instrumented_markDismissedByMatching_updatesDismissState() = runBlocking {
        val entity = NotificationEntity(
            key = "dismiss_target_1",
            packageName = "com.app.dismiss",
            appName = "DismissApp",
            title = "Promo Alert",
            content = "50% off today only",
            isDismissed = false
        )
        dao.insertNotification(entity)

        dao.markDismissedByMatching(
            key = "dismiss_target_1",
            packageName = "com.app.dismiss",
            title = "Promo Alert",
            content = "50% off today only"
        )

        val all = dao.getAllNotificationsList()
        val updated = all.first { it.key == "dismiss_target_1" }
        assertTrue(updated.isDismissed)
    }

    @Test
    fun instrumented_pinnedNotification_retainsPinnedStatus() = runBlocking {
        val entity = NotificationEntity(
            key = "pin_key_1",
            packageName = "com.app.pin",
            appName = "PinApp",
            title = "Keep this visible",
            content = "Important sticky message",
            isPinned = true
        )
        dao.insertNotification(entity)

        val retrieved = dao.getAllNotificationsList().first { it.key == "pin_key_1" }
        assertTrue(retrieved.isPinned)
    }

    @Test
    fun instrumented_updatePinnedStatus_and_updatePinnedStatusBatch() = runBlocking {
        val notif1 = NotificationEntity(
            key = "pin_batch_1",
            packageName = "com.test.pin1",
            appName = "Pin1",
            title = "Notice 1",
            content = "Body 1",
            isPinned = false
        )
        val notif2 = NotificationEntity(
            key = "pin_batch_2",
            packageName = "com.test.pin2",
            appName = "Pin2",
            title = "Notice 2",
            content = "Body 2",
            isPinned = false
        )
        dao.insertNotifications(listOf(notif1, notif2))

        dao.updatePinnedStatus("pin_batch_1", isPinned = true)
        val afterSingle = dao.getNotificationByKey("pin_batch_1")
        assertTrue(afterSingle?.isPinned == true)

        dao.updatePinnedStatusBatch(listOf("pin_batch_1", "pin_batch_2"), isPinned = false)
        val afterBatch1 = dao.getNotificationByKey("pin_batch_1")
        val afterBatch2 = dao.getNotificationByKey("pin_batch_2")
        assertFalse(afterBatch1?.isPinned == true)
        assertFalse(afterBatch2?.isPinned == true)
    }

    @Test
    fun instrumented_markAsReadBatch_updatesReadState() = runBlocking {
        val notif1 = NotificationEntity(
            key = "read_batch_1",
            packageName = "com.test.read",
            appName = "ReadApp",
            title = "Message 1",
            content = "Unread text",
            isRead = false
        )
        val notif2 = NotificationEntity(
            key = "read_batch_2",
            packageName = "com.test.read",
            appName = "ReadApp",
            title = "Message 2",
            content = "Unread text 2",
            isRead = false
        )
        dao.insertNotifications(listOf(notif1, notif2))

        dao.markAsReadBatch(listOf("read_batch_1", "read_batch_2"), autoDeleteOnRead = false)

        val updated1 = dao.getNotificationByKey("read_batch_1")
        val updated2 = dao.getNotificationByKey("read_batch_2")
        assertTrue(updated1?.isRead == true)
        assertTrue(updated2?.isRead == true)
    }

    @Test
    fun instrumented_markAllAsRead_and_unreadCountFlow() = runBlocking {
        val notifs = (1..3).map { index ->
            NotificationEntity(
                key = "unread_flow_$index",
                packageName = "com.test.unread",
                appName = "UnreadApp",
                title = "Alert $index",
                content = "Content $index",
                isRead = false
            )
        }
        dao.insertNotifications(notifs)

        val unreadInitial = dao.getUnreadCountFlow().first()
        assertEquals(3, unreadInitial)

        dao.markAllAsRead(autoDeleteOnRead = false)

        val unreadAfter = dao.getUnreadCountFlow().first()
        assertEquals(0, unreadAfter)
    }

    @Test
    fun instrumented_markDismissedWithReason_updatesDismissReasonAndTimestamp() = runBlocking {
        val entity = NotificationEntity(
            key = "dismiss_reason_key",
            packageName = "com.test.reason",
            appName = "ReasonApp",
            title = "Dismiss Me",
            content = "Reason test",
            isDismissed = false
        )
        dao.insertNotification(entity)

        val targetDismissTime = 9876543210L
        dao.markDismissedWithReason("dismiss_reason_key", reason = 2, dismissTime = targetDismissTime)

        val retrieved = dao.getNotificationByKey("dismiss_reason_key")
        assertTrue(retrieved?.isDismissed == true)
        assertEquals(2, retrieved?.dismissReason)
        assertEquals(targetDismissTime, retrieved?.dismissTime)
    }

    @Test
    fun instrumented_markDismissedBatch_dismissesAllSpecifiedKeys() = runBlocking {
        val n1 = NotificationEntity(
            key = "batch_dis_1",
            packageName = "p",
            appName = "A",
            title = "T1",
            content = "C1",
            isDismissed = false
        )
        val n2 = NotificationEntity(
            key = "batch_dis_2",
            packageName = "p",
            appName = "A",
            title = "T2",
            content = "C2",
            isDismissed = false
        )
        val n3 = NotificationEntity(
            key = "batch_dis_3",
            packageName = "p",
            appName = "A",
            title = "T3",
            content = "C3",
            isDismissed = false
        )
        dao.insertNotifications(listOf(n1, n2, n3))

        val dismissTime = 1234567890L
        dao.markDismissedBatch(listOf("batch_dis_1", "batch_dis_2"), dismissTime = dismissTime)

        val r1 = dao.getNotificationByKey("batch_dis_1")
        val r2 = dao.getNotificationByKey("batch_dis_2")
        val r3 = dao.getNotificationByKey("batch_dis_3")

        assertTrue(r1?.isDismissed == true)
        assertEquals(dismissTime, r1?.dismissTime)
        assertTrue(r2?.isDismissed == true)
        assertEquals(dismissTime, r2?.dismissTime)
        assertFalse(r3?.isDismissed == true)
    }

    @Test
    fun instrumented_markDismissedWithReasonBatch_updatesAllSpecifiedKeysWithReason() = runBlocking {
        val n1 = NotificationEntity(
            key = "batch_reason_1",
            packageName = "p",
            appName = "A",
            title = "T1",
            content = "C1",
            isDismissed = false
        )
        val n2 = NotificationEntity(
            key = "batch_reason_2",
            packageName = "p",
            appName = "A",
            title = "T2",
            content = "C2",
            isDismissed = false
        )
        dao.insertNotifications(listOf(n1, n2))

        val dismissTime = 5555555555L
        dao.markDismissedWithReasonBatch(
            keys = listOf("batch_reason_1", "batch_reason_2"),
            reason = 4,
            dismissTime = dismissTime
        )

        val r1 = dao.getNotificationByKey("batch_reason_1")
        val r2 = dao.getNotificationByKey("batch_reason_2")

        assertTrue(r1?.isDismissed == true)
        assertEquals(4, r1?.dismissReason)
        assertEquals(dismissTime, r1?.dismissTime)

        assertTrue(r2?.isDismissed == true)
        assertEquals(4, r2?.dismissReason)
        assertEquals(dismissTime, r2?.dismissTime)
    }

    @Test
    fun instrumented_markDismissedWithReasonByMatching_updatesDismissReasonAndState() = runBlocking {
        val notif = NotificationEntity(
            key = "reason_match_key",
            packageName = "com.match.pkg",
            appName = "MatchApp",
            title = "Special Offer",
            content = "Use code 50OFF",
            isDismissed = false
        )
        dao.insertNotification(notif)

        dao.markDismissedWithReasonByMatching(
            key = "reason_match_key",
            packageName = "com.match.pkg",
            title = "Special Offer",
            content = "Use code 50OFF",
            reason = 12
        )

        val retrieved = dao.getNotificationByKey("reason_match_key")
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isDismissed)
        assertEquals(12, retrieved.dismissReason)
        assertNotNull(retrieved.dismissTime)
    }

    @Test
    fun instrumented_insert_automaticallyPopulatesAppAndGroupEntities() = runBlocking {
        val notif = NotificationEntity(
            key = "auto_pop_key",
            packageName = "com.autopop.chat",
            appName = "AutoPop Chat",
            title = "Message from Alice",
            content = "Hey there!",
            groupKey = "chat_group_alpha",
            isDismissed = false
        )
        dao.insertNotification(notif)

        val app = database.appDao().getAppByPackage("com.autopop.chat")
        assertNotNull(app)
        assertEquals("AutoPop Chat", app?.appName)

        val group = database.notificationGroupDetailDao().getGroupByKey("chat_group_alpha")
        assertNotNull(group)
        assertEquals("com.autopop.chat", group?.packageName)
        assertEquals(1, group?.notificationCount)
        assertEquals(1, group?.activeCount)
        assertFalse(group!!.isDismissed)
    }

    @Test
    fun instrumented_updateEmbeddingsBatch_updatesMultipleNotifications() = runBlocking {
        val n1 = NotificationEntity(key = "emb_1", packageName = "p", appName = "A", title = "T1", content = "C1")
        val n2 = NotificationEntity(key = "emb_2", packageName = "p", appName = "A", title = "T2", content = "C2")
        val ids = dao.insertNotifications(listOf(n1, n2))
        val id1 = ids[0]
        val id2 = ids[1]

        val emb1 = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val emb2 = floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f)
        val emb1Bytes = Converters().fromFloatArray(emb1)!!
        val emb2Bytes = Converters().fromFloatArray(emb2)!!

        val batch = listOf(Pair(id1, emb1Bytes), Pair(id2, emb2Bytes))
        dao.updateEmbeddingsBatch(batch)

        val r1 = dao.getNotificationByKey("emb_1")
        val r2 = dao.getNotificationByKey("emb_2")
        assertNotNull(r1?.embedding)
        assertNotNull(r2?.embedding)
        assertArrayEquals(emb1, r1?.embedding, 0.001f)
        assertArrayEquals(emb2, r2?.embedding, 0.001f)
    }
}
