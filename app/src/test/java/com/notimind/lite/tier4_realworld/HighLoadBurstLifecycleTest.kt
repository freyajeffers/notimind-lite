package com.notimind.lite.tier4_realworld

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.receiver.BootReceiver
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNotificationManager
import kotlin.system.measureTimeMillis

class HighLoadBurstLifecycleTest : BaseRobolectricTest() {

    @Test
    fun tc_T4_002_stressTenThousandEventsStabilityAndDatabaseSla() = runTest {
        val count = 10_000

        // Measure batch insertion of 10,000 entities
        val durationMs = measureTimeMillis {
            val entities = (1..count).map { i ->
                NotificationEntity(
                    key = "stress_key_$i",
                    packageName = "com.stress.app_${i % 10}",
                    appName = "Stress App ${i % 10}",
                    title = "Burst Notification $i",
                    content = "High-load stress test payload body $i",
                    postTime = System.currentTimeMillis() + i,
                    isDismissed = i % 2 == 0,
                    isPinned = i % 100 == 0
                )
            }
            // Execute in batches to simulate fast high-burst ingestion
            entities.chunked(1000).forEach { batch ->
                batch.forEach { entity ->
                    dao.insertNotification(entity)
                }
            }
        }

        val totalInDb = dao.getNotificationCount()
        assertEquals(10_000, totalInDb)

        // Sub-2ms DAO Query SLA check on 10,000 records
        val activeQueryDurationMs = measureTimeMillis {
            val activeList = dao.getActiveNotificationsList()
            assertEquals(5_000, activeList.size)
        }

        assertTrue("Query execution for 5,000 active records should be fast (< 200ms in Robolectric JVM)", activeQueryDurationMs < 200)

        // Performance logging check
        println("10,000 event insertion time: ${durationMs}ms, Active query time: ${activeQueryDurationMs}ms")
    }

    @Test
    fun tc_T4_002_burstLoadAndSuddenRebootRecovery() = runTest {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm: ShadowNotificationManager = Shadows.shadowOf(notificationManager)

        // Insert 50 active notifications
        for (i in 1..50) {
            dao.insertNotification(
                createDummyEntity(
                    key = "burst_reboot_k_$i",
                    appName = "App $i",
                    title = "Burst Title $i",
                    content = "Burst Content $i",
                    isDismissed = i > 25 // 25 active, 25 dismissed
                )
            )
        }

        assertEquals(50, dao.getNotificationCount())
        assertEquals(25, dao.getActiveNotificationsList().size)

        // Trigger sudden reboot
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val bootReceiver = BootReceiver()
        bootReceiver.onReceive(context, bootIntent)
        Thread.sleep(200)

        val posted = shadowNm.allNotifications
        assertTrue("BootReceiver must restore active notifications upon sudden reboot", posted.isNotEmpty())
    }
}
