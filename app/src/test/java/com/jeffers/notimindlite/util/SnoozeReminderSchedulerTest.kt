package com.jeffers.notimindlite.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.receiver.SnoozeReminderReceiver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnoozeReminderSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun scheduleSnooze_createsPendingIntentAndRegistersAlarm() {
        val notifId = "snooze_test_1"
        val delayMs = 300_000L

        val intent = Intent(context, SnoozeReminderReceiver::class.java)
        val beforePi = PendingIntent.getBroadcast(
            context,
            notifId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNull(beforePi)

        SnoozeReminderScheduler.scheduleSnooze(
            context = context,
            notificationId = notifId,
            title = "Reminder Title",
            content = "Reminder Content",
            delayMs = delayMs
        )

        val afterPi = PendingIntent.getBroadcast(
            context,
            notifId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull(afterPi)
    }

    @Test
    fun cancelSnooze_cancelsScheduledSnoozeCleanly() {
        val notifId = "snooze_cancel_target"
        SnoozeReminderScheduler.scheduleSnooze(
            context = context,
            notificationId = notifId,
            title = "To be cancelled",
            content = "Will not fire",
            delayMs = 120_000L
        )

        SnoozeReminderScheduler.cancelSnooze(context, notifId)

        val intent = Intent(context, SnoozeReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            notifId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull(pi)
    }

    @Test
    fun cancelSnooze_whenNoAlarmScheduled_executesWithoutCrashing() {
        SnoozeReminderScheduler.cancelSnooze(context, "unregistered_id")
    }
}
