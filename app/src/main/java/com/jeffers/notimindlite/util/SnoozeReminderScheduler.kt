package com.jeffers.notimindlite.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

/**
 * SnoozeReminderScheduler handles scheduling local notifications for snoozed items.
 * It allows users to set a reminder for a notification they aren't ready to handle yet.
 */
object SnoozeReminderScheduler {
    private const val TAG = "SnoozeScheduler"
    private const val CHANNEL_ID = "snooze_reminders"

    /**
     * Schedules a reminder notification for a specific notification item.
     * @param context The application context.
     * @param notificationId The ID of the notification being snoozed.
     * @param title The title of the notification.
     * @param content The content of the notification.
     * @param delayMs The delay in milliseconds before the reminder is triggered.
     */
    fun scheduleSnooze(
        context: Context,
        notificationId: String,
        title: String,
        content: String,
        delayMs: Long
    ) {
        try {
            val intent = Intent(context, com.jeffers.notimindlite.receiver.SnoozeReminderReceiver::class.java).apply {
                putExtra("NOTIF_ID", notificationId)
                putExtra("ORIGINAL_TITLE", title)
                putExtra("ORIGINAL_CONTENT", content)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + delayMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            
            
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze reminder", e)
        }
    }

    fun cancelSnooze(context: Context, notificationId: String) {
        try {
            val intent = Intent(context, com.jeffers.notimindlite.receiver.SnoozeReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel snooze reminder", e)
        }
    }
}
