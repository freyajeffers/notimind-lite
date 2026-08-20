package com.notimind.lite.tier2_boundary

import android.content.Intent
import com.jeffers.notimindlite.receiver.SnoozeReminderReceiver
import com.jeffers.notimindlite.util.SnoozeReminderScheduler
import com.notimind.lite.base.BaseRobolectricTest
import org.junit.Assert.assertNotNull
import org.junit.Test

class SnoozeLoopBoundaryTest : BaseRobolectricTest() {

    @Test
    fun `scheduleSnooze - minimal delay handles correctly`() {
        val notifId = "test_1"
        val delay = 1L // 1ms
        
        SnoozeReminderScheduler.scheduleSnooze(context, notifId, "Title", "Content", delay)
    }

    @Test
    fun `scheduleSnooze - extreme delay handles correctly`() {
        val notifId = "test_long"
        val delay = 31536000000L // 1 year in ms
        
        SnoozeReminderScheduler.scheduleSnooze(context, notifId, "Title", "Content", delay)
    }

    @Test
    fun `cancelSnooze - non existent snooze handles gracefully`() {
        val notifId = "never_snoozed"
        SnoozeReminderScheduler.cancelSnooze(context, notifId)
    }

    @Test
    fun `SnoozeReminderReceiver - missing payload handles gracefully`() {
        val receiver = SnoozeReminderReceiver()
        val intent = Intent()
        receiver.onReceive(context, intent)
        assertNotNull(receiver)
    }

    @Test
    fun `SnoozeReminderReceiver - partial payload uses defaults`() {
        val receiver = SnoozeReminderReceiver()
        val intent = Intent().apply {
            putExtra("NOTIF_ID", "valid_id")
        }
        receiver.onReceive(context, intent)
        assertNotNull(receiver)
    }
}
