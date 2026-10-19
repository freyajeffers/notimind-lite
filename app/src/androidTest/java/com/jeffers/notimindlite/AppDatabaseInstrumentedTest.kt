package com.jeffers.notimindlite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}
