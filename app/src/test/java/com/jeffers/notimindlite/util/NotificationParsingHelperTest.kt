package com.jeffers.notimindlite.util

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationParsingHelperTest {
    private lateinit var helper: NotificationParsingHelper

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = NotificationParsingHelper(context.packageManager)
    }

    @Test
    fun parseTitleAndContent_nullExtras_returnsEmptyStrings() {
        val result = helper.parseTitleAndContent(null)
        assertEquals("", result.title)
        assertEquals("", result.content)
        assertNull(result.subText)
        assertNull(result.bigText)
    }

    @Test
    fun parseTitleAndContent_standardExtras_extractsFieldsCorrectly() {
        val bundle = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Meeting Reminder")
            putCharSequence(Notification.EXTRA_TEXT, "Sync at 2:00 PM")
            putCharSequence(Notification.EXTRA_SUB_TEXT, "Calendar")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Detailed agenda for the meeting")
        }

        val result = helper.parseTitleAndContent(bundle)
        assertEquals("Meeting Reminder", result.title)
        assertEquals("Sync at 2:00 PM", result.content)
        assertEquals("Calendar", result.subText)
        assertEquals("Detailed agenda for the meeting", result.bigText)
    }

    @Test
    fun parseTitleAndContent_emptyTitleFallsBackToConversationTitle() {
        val bundle = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "")
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "Project Team Chat")
            putCharSequence(Notification.EXTRA_TEXT, "Hello team!")
        }

        val result = helper.parseTitleAndContent(bundle)
        assertEquals("Project Team Chat", result.title)
        assertEquals("Hello team!", result.content)
    }

    @Test
    fun parseTitleAndContent_emptyContentFallsBackToBigTextOrSummaryText() {
        val bundle = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Security Alert")
            putCharSequence(Notification.EXTRA_TEXT, "")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "A new login was detected on Linux.")
        }

        val result = helper.parseTitleAndContent(bundle)
        assertEquals("Security Alert", result.title)
        assertEquals("A new login was detected on Linux.", result.content)
    }

    @Test
    fun extractInboxLinesJson_withValidLines_serializesToJsonArray() {
        val lines = arrayOf<CharSequence>("First line", "Second line", "Third line")
        val bundle = Bundle().apply {
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES, lines)
        }

        val json = helper.extractInboxLinesJson(bundle)
        assertNotNull(json)
        assertEquals("[\"First line\",\"Second line\",\"Third line\"]", json)
    }

    @Test
    fun extractInboxLinesJson_emptyOrNull_returnsNull() {
        assertNull(helper.extractInboxLinesJson(null))
        assertNull(helper.extractInboxLinesJson(Bundle()))
    }

    @Test
    fun buildActionLabelsJson_nullOrEmpty_returnsNull() {
        assertNull(helper.buildActionLabelsJson(null))
        assertNull(helper.buildActionLabelsJson(emptyArray()))
    }

    @Test
    fun buildActionLabelsJson_withValidActions_serializesLabelsCorrectly() {
        val action1 = Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Reply", null).build()
        val action2 = Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Mark as Read", null).build()
        val json = helper.buildActionLabelsJson(arrayOf(action1, action2))

        assertNotNull(json)
        assertEquals("[\"Reply\",\"Mark as Read\"]", json)
    }

    @Test
    fun buildActionLabelsJson_withNullTitleAction_usesFallbackLabel() {
        val action = Notification.Action.Builder(null as android.graphics.drawable.Icon?, null, null).build()
        val json = helper.buildActionLabelsJson(arrayOf(action))

        assertNotNull(json)
        assertEquals("[\"Action 0\"]", json)
    }

    @Test
    fun resolveAppName_unknownPackage_returnsPackageNameFallback() {
        val resolved = helper.resolveAppName("com.nonexistent.fake.app")
        assertEquals("com.nonexistent.fake.app", resolved)
    }

    @Test
    fun safeSmallIconRes_withoutSmallIcon_returnsZero() {
        val notification = Notification()
        val iconRes = helper.safeSmallIconRes(notification)
        assertEquals(0, iconRes)
    }

    @Test
    fun safeLaunchIntentUri_unknownPackage_returnsNull() {
        val uri = helper.safeLaunchIntentUri("com.nonexistent.fake.app")
        assertNull(uri)
    }
}
