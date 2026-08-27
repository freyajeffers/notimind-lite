package com.notimind.lite.tier4_realworld

import android.content.Context
import android.provider.Settings
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Robolectric

class PermissionRevocationLifecycleTest : BaseRobolectricTest() {

    @Test
    fun tc_T4_003_permissionRevocationServiceInterruptionAndRecoveryLifecycle() = runTest {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        val componentNameStr = "${context.packageName}/${NotificationLoggerService::class.java.canonicalName}"
        android.provider.Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", componentNameStr)

        // 1. Initial State: Listener permission granted, ingest SBN-1
        val sbn1 = createMockStatusBarNotification(
            key = "perm_k1",
            packageName = "com.test.app1",
            title = "Title 1",
            text = "Content 1"
        )
        service.onNotificationPosted(sbn1)
        Thread.sleep(100)

        assertEquals("SBN 1 should be logged when permission is active", 1, dao.getNotificationCount())

        // 2. Permission revocation simulation: settings listener component String cleared
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "NONE")

        val sbn2 = createMockStatusBarNotification(
            key = "perm_k2",
            packageName = "com.test.app2",
            title = "Title 2",
            text = "Content 2"
        )

        // Attempt notification post while revoked — should handle safely without throwing
        try {
            service.onNotificationPosted(sbn2)
            Thread.sleep(100)
            assertTrue(true)
        } catch (e: Exception) {
            fail("Service must not crash when receiving notification during permission revocation: ${e.message}")
        }

        // 3. Permission re-grant simulation & Service rebind
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", componentNameStr)
        NotificationLoggerService.rebindService(context)

        val sbn3 = createMockStatusBarNotification(
            key = "perm_k3",
            packageName = "com.test.app3",
            title = "Title 3",
            text = "Content 3"
        )
        service.onNotificationPosted(sbn3)
        Thread.sleep(100)

        val allLogs = dao.getAllNotifications().first()
        val expectedKey = "${sbn3.packageName}|${sbn3.id}|${sbn3.tag}"
        assertTrue("Service should recover logging after permission re-grant", allLogs.any { it.key == expectedKey })

        serviceController.destroy()
    }
}
