package com.jeffers.notimindlite.receiver

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnoozeReminderReceiverTest {
    private lateinit var context: Context
    private lateinit var receiver: SnoozeReminderReceiver
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        receiver = SnoozeReminderReceiver()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun onReceive_missingNotifIdExtra_returnsEarlyWithoutCreatingChannel() {
        val emptyIntent = Intent()
        receiver.onReceive(context, emptyIntent)

        assertNull(notificationManager.getNotificationChannel("snooze_reminders"))
    }

    @Test
    fun onReceive_withNotifId_createsChannelAndPostsNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val intent = Intent().apply {
            putExtra("NOTIF_ID", "test_reminder_1")
            putExtra("ORIGINAL_TITLE", "Doctor Appointment")
            putExtra("ORIGINAL_CONTENT", "Check-up at 3 PM")
        }

        receiver.onReceive(context, intent)

        val channel = notificationManager.getNotificationChannel("snooze_reminders")
        assertNotNull(channel)
        assertEquals("Snooze Reminders", channel.name.toString())

        val shadowNotificationManager = shadowOf(notificationManager)
        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val posted = notifications[0]
        assertNotNull(posted)
    }

    @Test
    fun onReceive_withDefaultExtras_createsNotificationWithFallbackText() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val intent = Intent().apply {
            putExtra("NOTIF_ID", "test_reminder_defaults")
        }

        receiver.onReceive(context, intent)

        val shadowNotificationManager = shadowOf(notificationManager)
        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
    }
}
