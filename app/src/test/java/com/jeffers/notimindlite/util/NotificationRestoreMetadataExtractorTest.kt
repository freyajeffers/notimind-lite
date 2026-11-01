package com.jeffers.notimindlite.util

import android.app.Notification
import android.app.Notification.Builder
import android.app.Notification.MessagingStyle
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationRestoreMetadataExtractorTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun capturesMessagingStyleVisualDataAndAppliesCapturePrivacySettings() {
        val sender = Person.Builder().setName("sender@example.com").build()
        val messagingStyle = MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle("Private conversation")
            .setGroupConversation(true)
            .addMessage("Reply to user@example.com with code 654321", 1_700_000_000_123L, sender)
        val notification = Builder(context, "restore-test")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Chat")
            .setContentText("New message")
            .setStyle(messagingStyle)
            .setColor(0xFF123456.toInt())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setNumber(4)
            .setWhen(1_700_000_000_000L)
            .setShowWhen(false)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(100, 30, false)
            .setLargeIcon(Icon.createWithResource(context, R.drawable.ic_launcher_vector))
            .build()

        val metadata = JSONObject(
            NotificationRestoreMetadataExtractor.capture(
                notification = notification,
                redactPii = true,
                anonymizeTitles = true
            )
        )
        val messages = metadata.getJSONArray("messages")
        val message = messages.getJSONObject(0)

        assertTrue(metadata.getString("template").endsWith("MessagingStyle"))
        assertEquals("[REDACTED-TITLE]", metadata.getString("conversationTitle"))
        assertEquals(0xFF123456.toInt(), metadata.getInt("color"))
        assertEquals(Notification.VISIBILITY_PUBLIC, metadata.getInt("visibility"))
        assertEquals(4, metadata.getInt("number"))
        assertEquals(100, metadata.getInt("progressMax"))
        assertEquals(30, metadata.getInt("progress"))
        assertTrue(metadata.getBoolean("usesChronometer"))
        assertTrue(metadata.getBoolean("chronometerCountDown"))
        assertEquals("[REDACTED-EMAIL]", message.getString("sender"))
        assertTrue(message.getString("text").contains("[REDACTED-EMAIL]"))
        assertTrue(message.getString("text").contains("[REDACTED-OTP]"))
        assertEquals(R.drawable.ic_launcher_vector, metadata.getInt("largeIconResourceId"))
        assertEquals(context.packageName, metadata.getString("largeIconPackage"))
    }

    @Test
    fun captureCanPreserveTextWhenPiiRedactionIsDisabled() {
        val messagingStyle = MessagingStyle(Person.Builder().setName("Me").build())
            .addMessage("Receipt for user@example.com", 1_700_000_000_123L, null as Person?)
        val notification = Builder(context, "restore-test")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Payment")
            .setContentText("Receipt for user@example.com")
            .setStyle(messagingStyle)
            .build()

        val metadata = JSONObject(
            NotificationRestoreMetadataExtractor.capture(
                notification = notification,
                redactPii = false,
                anonymizeTitles = false
            )
        )
        val message = metadata.getJSONArray("messages").getJSONObject(0)

        assertEquals("Receipt for user@example.com", message.getString("text"))
    }
}
