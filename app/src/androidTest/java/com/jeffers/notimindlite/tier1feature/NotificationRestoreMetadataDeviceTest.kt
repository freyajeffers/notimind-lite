package com.jeffers.notimindlite.tier1feature

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Parcelable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.receiver.BootReceiver
import com.jeffers.notimindlite.service.BootConsolidationResult
import com.jeffers.notimindlite.service.RestoredNotificationFactory
import com.jeffers.notimindlite.util.NotificationRestoreMetadataExtractor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRestoreMetadataDeviceTest {
    @Test
    fun messagingPresentationIsCapturedRedactedAndRebuilt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timestamp = 1_700_000_000_123L
        val style = Notification
            .MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle("Team chat")
            .setGroupConversation(true)
            .addMessage(
                Notification.MessagingStyle.Message(
                    "Please review user@example.com",
                    timestamp,
                    Person.Builder().setName("Alice").build()
                )
            )
        val original = Notification
            .Builder(context, "restore_metadata_test")
            .setSmallIcon(R.drawable.ic_launcher_vector)
            .setLargeIcon(Icon.createWithResource(context, R.drawable.ic_launcher_vector))
            .setContentTitle("Team chat")
            .setContentText("New message")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(style)
            .setColor(0xFF334455.toInt())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setNumber(4)
            .setWhen(timestamp)
            .setShowWhen(false)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(100, 60, false)
            .setLocalOnly(true)
            .setColorized(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setOngoing(true)
            .build()

        val metadataJson = NotificationRestoreMetadataExtractor.capture(
            notification = original,
            redactPii = true,
            anonymizeTitles = false
        )
        val metadata = JSONObject(metadataJson)
        val capturedMessage = metadata.getJSONArray("messages").getJSONObject(0)
        assertEquals("Please review [REDACTED-EMAIL]", capturedMessage.getString("text"))
        assertEquals("Alice", capturedMessage.getString("sender"))
        assertEquals("Team chat", metadata.getString("conversationTitle"))
        assertEquals(60, metadata.getInt("progress"))
        assertTrue(metadata.getBoolean("usesChronometer"))

        val record = NotificationEntity(
            key = "device_restore_metadata_test",
            packageName = context.packageName,
            appName = "NotiMind Lite",
            title = "Team chat",
            content = "New message",
            postTime = timestamp,
            category = Notification.CATEGORY_MESSAGE,
            isOngoing = true,
            isClearable = false,
            smallIconRes = R.drawable.ic_launcher_vector,
            restoreMetadataJson = metadataJson
        )
        val restored = RestoredNotificationFactory.create(
            context = context,
            channelId = BootReceiver.CHANNEL_ID_RESTORED,
            item = BootConsolidationResult(
                summaryTitle = record.title,
                summaryContent = record.content,
                appName = record.appName,
                originalIds = listOf(record.id)
            ),
            originals = listOf(record),
            notificationId = 1_044,
            redactPii = true
        )
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
            restored.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        )

        assertEquals(0xFF334455.toInt(), restored.color)
        assertEquals(Notification.VISIBILITY_PUBLIC, restored.visibility)
        assertEquals(4, restored.number)
        assertEquals(timestamp, restored.`when`)
        assertEquals(60, restored.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(false, restored.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        assertTrue(restored.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNotNull(restored.getLargeIcon())
        assertEquals("Please review [REDACTED-EMAIL]", messages.single().text.toString())
    }
}
