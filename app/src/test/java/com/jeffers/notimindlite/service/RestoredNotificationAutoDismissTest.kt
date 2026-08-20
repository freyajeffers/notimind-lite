package com.jeffers.notimindlite.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoredNotificationAutoDismissTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        RestoredNotificationManager.clearRestoredState()
    }

    @Test
    fun testRestoredNotificationRegistration_tracksPackageAndKey() {
        val key = "0|com.example.chat|1|null|1000"
        val pkg = "com.example.chat"
        val notifId = 4001

        RestoredNotificationManager.markAsRestored(key, pkg, notifId)

        assertTrue(RestoredNotificationManager.isRestored(key))
        assertEquals(notifId, RestoredNotificationManager.getRestoredNotifIdForPackage(pkg))
    }

    @Test
    fun testOnOriginalAppNotificationPosted_autoDismissesRestoredNotification() {
        val key = "0|com.example.chat|1|null|1000"
        val pkg = "com.example.chat"
        val notifId = 4002

        RestoredNotificationManager.markAsRestored(key, pkg, notifId)
        assertEquals(notifId, RestoredNotificationManager.getRestoredNotifIdForPackage(pkg))

        // When original app posts a new notification
        RestoredNotificationManager.onOriginalAppNotificationPosted(context, pkg)

        // Restored notification must be cancelled and unregistered
        assertNull(RestoredNotificationManager.getRestoredNotifIdForPackage(pkg))
        assertFalse(RestoredNotificationManager.isRestored(key))
    }
}
