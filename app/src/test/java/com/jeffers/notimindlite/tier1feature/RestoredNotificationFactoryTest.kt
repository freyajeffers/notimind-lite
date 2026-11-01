package com.jeffers.notimindlite.tier1feature

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Parcelable
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.R
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.receiver.BootReceiver
import com.jeffers.notimindlite.service.BootConsolidationResult
import com.jeffers.notimindlite.service.RestoredNotificationFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoredNotificationFactoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun rebuiltNotificationKeepsOriginalTextStyleTimestampAndSourceIcon() {
        val original = notification(
            id = 42,
            title = "Delivery update",
            content = "Your package is nearby",
            subText = "Courier",
            bigText = "Your package is nearby and will arrive shortly.",
            category = Notification.CATEGORY_MESSAGE,
            smallIconRes = R.drawable.ic_launcher_vector
        )

        val restored = RestoredNotificationFactory.create(
            context = context,
            channelId = BootReceiver.CHANNEL_ID_RESTORED,
            item = BootConsolidationResult(
                summaryTitle = "Delivery update",
                summaryContent = "Your package is nearby",
                appName = original.appName,
                originalIds = listOf(original.id)
            ),
            originals = listOf(original),
            notificationId = 1042,
            redactPii = false
        )

        assertEquals(original.title, restored.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(original.content, restored.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(original.subText, restored.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertEquals(original.bigText, restored.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals(original.postTime, restored.`when`)
        assertEquals(original.category, restored.category)
        assertEquals(Icon.TYPE_BITMAP, restored.smallIcon?.type)
        assertNotNull(restored.contentIntent)
    }

    @Test
    fun restoredNotificationCannotBeClearedFromTheSystemShade() {
        val original = notification(id = 41, title = "Reminder", content = "Review this")
        val restored = RestoredNotificationFactory.create(
            context = context,
            channelId = BootReceiver.CHANNEL_ID_RESTORED,
            item = BootConsolidationResult(
                summaryTitle = original.title,
                summaryContent = original.content,
                appName = original.appName,
                originalIds = listOf(original.id)
            ),
            originals = listOf(original),
            notificationId = 1041,
            redactPii = false
        )

        assertTrue(restored.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(restored.flags and Notification.FLAG_NO_CLEAR != 0)
        assertTrue(restored.flags and Notification.FLAG_AUTO_CANCEL == 0)
    }

    @Test
    fun inboxStyleUsesSavedLinesAndRedactsSensitiveTextWhenEnabled() {
        val original = notification(
            id = 43,
            title = "Account activity",
            content = "New message from user@example.com",
            inboxLinesJson = JSONArray(listOf("user@example.com sent a message", "Code 123456 received")).toString()
        )

        val restored = RestoredNotificationFactory.create(
            context = context,
            channelId = BootReceiver.CHANNEL_ID_RESTORED,
            item = BootConsolidationResult(
                summaryTitle = original.title,
                summaryContent = original.content,
                appName = original.appName,
                originalIds = listOf(original.id)
            ),
            originals = listOf(original),
            notificationId = 1043,
            redactPii = true
        )
        val lines = restored.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()

        assertEquals(
            "New message from [REDACTED-EMAIL]",
            restored.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("[REDACTED-EMAIL]"))
        assertTrue(lines[1].contains("[REDACTED-OTP]"))
    }

    @Test
    fun restoredNotificationRebuildsMessagingStyleAndVisualAttributes() {
        val message = JSONObject()
            .put("text", "Please review user@example.com")
            .put("sender", "Alice")
            .put("timestamp", 1_700_000_000_123L)
            .put("historic", false)
        val metadata = JSONObject()
            .put("template", "android.app.Notification\$MessagingStyle")
            .put("messagingUserName", "Me")
            .put("conversationTitle", "Team chat")
            .put("isGroupConversation", true)
            .put("messages", JSONArray().put(message))
            .put("color", 0xFF334455.toInt())
            .put("visibility", Notification.VISIBILITY_PUBLIC)
            .put("number", 4)
            .put("when", 1_700_000_000_456L)
            .put("showWhen", false)
            .put("usesChronometer", true)
            .put("chronometerCountDown", true)
            .put("progressMax", 100)
            .put("progress", 60)
            .put("progressIndeterminate", false)
            .put("localOnly", true)
            .put("largeIconResourceId", R.drawable.ic_launcher_vector)
            .put("largeIconPackage", context.packageName)
            .toString()
        val original = notification(
            id = 44,
            title = "Team chat",
            content = "New message",
            restoreMetadataJson = metadata
        )

        val restored = RestoredNotificationFactory.create(
            context = context,
            channelId = BootReceiver.CHANNEL_ID_RESTORED,
            item = BootConsolidationResult(
                summaryTitle = original.title,
                summaryContent = original.content,
                appName = original.appName,
                originalIds = listOf(original.id)
            ),
            originals = listOf(original),
            notificationId = 1044,
            redactPii = true
        )
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
            restored.extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        )

        assertEquals(0xFF334455.toInt(), restored.color)
        assertEquals(Notification.VISIBILITY_PUBLIC, restored.visibility)
        assertNotNull(restored.getLargeIcon())
        assertEquals(4, restored.number)
        assertEquals(1_700_000_000_456L, restored.`when`)
        assertEquals(60, restored.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, restored.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(false, restored.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        val restoredMessage = messages.single()
        val restoredSenderName = restoredMessage.senderPerson?.name
        assertEquals("Please review [REDACTED-EMAIL]", restoredMessage.text.toString())
        assertEquals("Alice", restoredSenderName?.toString())
    }

    private fun notification(
        id: Long,
        title: String,
        content: String,
        subText: String? = null,
        bigText: String? = null,
        inboxLinesJson: String? = null,
        category: String? = null,
        smallIconRes: Int = 0,
        restoreMetadataJson: String? = null
    ) = NotificationEntity(
        id = id,
        key = "${context.packageName}|$id",
        packageName = context.packageName,
        appName = "Source app",
        title = title,
        content = content,
        postTime = 1_700_000_000_000L,
        subText = subText,
        bigText = bigText,
        inboxLinesJson = inboxLinesJson,
        category = category,
        smallIconRes = smallIconRes,
        restoreMetadataJson = restoreMetadataJson
    )
}
