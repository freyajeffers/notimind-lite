package com.notimind.lite.tier4_realworld

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.jeffers.notimindlite.receiver.BootReceiver
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNotificationManager

class EndToEndAppLifecycleTest : BaseRobolectricTest() {

    @Test
    fun tc_T4_001_fullEndToEndApplicationLifecycleSimulation() = runTest {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm: ShadowNotificationManager = Shadows.shadowOf(notificationManager)

        // Phase 1: Initial Startup verification
        assertEquals("Database should start empty", 0, dao.getNotificationCount())

        // Phase 2: Ingest 5 notifications from different apps
        val apps = listOf(
            Triple("com.whatsapp", "WhatsApp", "Mom: Don't forget milk!"),
            Triple("com.google.android.gm", "Gmail", "Security Alert: New login"),
            Triple("com.slack", "Slack", "#general: Deploying v1.2"),
            Triple("com.twitter.android", "X", "X: New follower notification"),
            Triple("com.google.android.calendar", "Calendar", "Calendar: Sync meeting at 3pm")
        )

        val sbns = apps.mapIndexed { idx, (pkg, appName, titleContent) ->
            val parts = titleContent.split(": ")
            val title = parts[0]
            val text = parts[1]
            createMockStatusBarNotification(
                key = "$pkg|${100 + idx}|null|${1000 + idx}",
                packageName = pkg,
                title = title,
                text = text,
                postTime = System.currentTimeMillis() + idx * 1000
            )
        }

        for (sbn in sbns) {
            service.onNotificationPosted(sbn)
        }
        Thread.sleep(150)

        assertEquals("5 notifications should be ingested", 5, dao.getNotificationCount())
        assertEquals("5 notifications should be active", 5, dao.getActiveNotificationsList().size)

        // Phase 3: User dismisses 2 notifications (WhatsApp and X)
        service.onNotificationRemoved(sbns[0]) // WhatsApp
        service.onNotificationRemoved(sbns[3]) // X
        Thread.sleep(150)

        assertEquals("Active count should drop to 3", 3, dao.getActiveNotificationsList().size)
        assertEquals("Total count should remain 5", 5, dao.getNotificationCount())

        // Phase 4: Device reboot simulation
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val bootReceiver = BootReceiver()
        bootReceiver.onReceive(context, bootIntent)
        Thread.sleep(200)

        assertTrue("BootReceiver should re-post active notifications", shadowNm.allNotifications.isNotEmpty())

        // Phase 5: History Search / Filter validation
        val allHistory = dao.getAllNotifications().first()
        val slackMatch = allHistory.filter { it.appName == "Slack" || it.title.contains("Slack") || it.content.contains("v1.2") }
        assertEquals(1, slackMatch.size)
        assertEquals("Slack", slackMatch[0].packageName.replace("com.", "").replace("android", "").capitalize())

        // Phase 6: Log Purge & Synchronization Reset
        dao.clearAll()
        assertEquals(0, dao.getNotificationCount())
        assertTrue("Active notifications should be empty", dao.getActiveNotificationsList().isEmpty())
        assertTrue("History log should be empty", dao.getAllNotifications().first().isEmpty())

        serviceController.destroy()
    }

    private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
