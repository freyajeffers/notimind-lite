package com.jeffers.notimindlite.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * SnoozeReminderReceiver handles the trigger of the snooze alarm.
 * It creates a local notification to remind the user of the snoozed item.
 */
class SnoozeReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SnoozeReceiver"
        private const val CHANNEL_ID = "snooze_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getStringExtra("NOTIF_ID") ?: return
        val title = intent.getStringExtra("ORIGINAL_TITLE") ?: "Snoozed Notification"
        val content = intent.getStringExtra("ORIGINAL_CONTENT") ?: "Time to review this item."

        

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Snooze Reminder")
            .setContentText("$title: $content")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(notifId.hashCode(), notification)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Snooze Reminders"
            val descriptionText = "Notifications for snoozed items"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
