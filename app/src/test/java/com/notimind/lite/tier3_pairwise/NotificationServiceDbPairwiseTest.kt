package com.notimind.lite.tier3_pairwise

import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Robolectric

class NotificationServiceDbPairwiseTest : BaseRobolectricTest() {

    @Test
    fun tc_T3_001_serviceAndRoomDbPairwiseNotificationLifecycle() = runTest {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        val sbn = createMockStatusBarNotification(
            key = "com.chat.app|101|null|1001",
            packageName = "com.chat.app",
            title = "Alice",
            text = "Hey there!"
        )

        // 1. Post notification
        service.onNotificationPosted(sbn)
        Thread.sleep(100)

        val activeList = dao.getActiveNotificationsList()
        assertEquals(1, activeList.size)
        assertEquals("com.chat.app|101|null|1001", activeList[0].key)
        assertFalse(activeList[0].isDismissed)

        // 2. Remove notification
        service.onNotificationRemoved(sbn)
        Thread.sleep(100)

        assertEquals(0, dao.getActiveNotificationsList().size)
        val history = dao.getAllNotifications().first()
        assertEquals(1, history.size)
        assertTrue(history[0].isDismissed)

        serviceController.destroy()
    }

    @Test
    fun tc_T3_005_concurrentIngestionDuringBootRecoveryExecution() = runTest {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        // Insert initial active notifications
        dao.insertNotification(createDummyEntity(key = "boot_k1", isDismissed = false))
        dao.insertNotification(createDummyEntity(key = "boot_k2", isDismissed = false))

        val jobs = listOf(
            async(Dispatchers.IO) {
                // Simulate service ingestion
                val sbn = createMockStatusBarNotification(
                    key = "live_k3",
                    packageName = "com.live.app",
                    title = "Live Title",
                    text = "Live Content"
                )
                service.onNotificationPosted(sbn)
            },
            async(Dispatchers.IO) {
                // Read active notifications
                dao.getActiveNotificationsList()
            }
        )
        jobs.awaitAll()

        Thread.sleep(100)
        assertTrue("Database should have 3 total items", dao.getNotificationCount() >= 3)
        serviceController.destroy()
    }
}
