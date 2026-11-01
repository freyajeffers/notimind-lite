package com.jeffers.notimindlite.util

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.R
import io.mockk.every
import io.mockk.mockk
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
    fun parseTitleAndContent_fallsBackToSummaryTextWhenTextAndBigTextEmpty() {
        val bundle = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Download Complete")
            putCharSequence(Notification.EXTRA_TEXT, "")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "   ")
            putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "1 file downloaded")
        }

        val result = helper.parseTitleAndContent(bundle)
        assertEquals("Download Complete", result.title)
        assertEquals("1 file downloaded", result.content)
    }

    @Test
    fun parseTitleAndContent_allBlankContentReturnsEmptyString() {
        val bundle = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "")
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "")
            putCharSequence(Notification.EXTRA_TEXT, "   ")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "   ")
            putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "   ")
        }

        val result = helper.parseTitleAndContent(bundle)
        assertEquals("", result.title)
        assertEquals("", result.content)
    }

    @Test
    fun extractInboxLinesJson_withStringArray_serializesSuccessfully() {
        val bundle = Bundle().apply {
            putStringArray(Notification.EXTRA_TEXT_LINES, arrayOf("Line 1", "Line 2"))
        }

        val json = helper.extractInboxLinesJson(bundle)
        assertNotNull(json)
        assertEquals("[\"Line 1\",\"Line 2\"]", json)
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
    fun resolveAppName_knownPackage_returnsLabel() {
        val packageManager = mockk<PackageManager>()
        val appInfo = mockk<ApplicationInfo>()
        every {
            packageManager.getApplicationInfo(
                "com.example.app",
                any<PackageManager.ApplicationInfoFlags>()
            )
        } returns appInfo
        every { packageManager.getApplicationLabel(appInfo) } returns "Example App"

        assertEquals(
            "Example App",
            NotificationParsingHelper(packageManager).resolveAppName("com.example.app")
        )
    }

    @Test
    fun resolveAppName_securityException_returnsPackageNameFallback() {
        val packageManager = mockk<PackageManager>()
        every {
            packageManager.getApplicationInfo(any(), any<PackageManager.ApplicationInfoFlags>())
        } throws SecurityException("Package access denied")

        assertEquals(
            "com.example.app",
            NotificationParsingHelper(packageManager).resolveAppName("com.example.app")
        )
    }

    @Test
    fun extractInboxLinesJson_classCastFallsBackToStringArray() {
        val extras = mockk<Bundle>()
        every {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        } throws ClassCastException("Not a CharSequence array")
        every { extras.getStringArray(Notification.EXTRA_TEXT_LINES) } returns arrayOf("First", "Second")

        assertEquals(
            "[\"First\",\"Second\"]",
            helper.extractInboxLinesJson(extras)
        )
    }

    @Test
    fun safeSmallIconRes_withResourceIcon_returnsResourceId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceId = R.drawable.ic_launcher_vector
        val notification = Notification
            .Builder(context, "test-channel")
            .setSmallIcon(resourceId)
            .build()

        assertEquals(resourceId, helper.safeSmallIconRes(notification))
    }

    @Test
    fun safeSmallIconRes_illegalState_returnsZero() {
        val notification = mockk<Notification>()
        every { notification.smallIcon } throws IllegalStateException("Icon unavailable")

        assertEquals(0, helper.safeSmallIconRes(notification))
    }

    @Test
    fun safeLaunchIntentUri_validIntent_returnsIntentUri() {
        val packageManager = mockk<PackageManager>()
        val intent = Intent(Intent.ACTION_MAIN)
        every { packageManager.getLaunchIntentForPackage("com.example.app") } returns intent

        assertEquals(
            intent.toUri(Intent.URI_INTENT_SCHEME),
            NotificationParsingHelper(packageManager).safeLaunchIntentUri("com.example.app")
        )
    }

    @Test
    fun safeLaunchIntentUri_securityAndArgumentFailures_returnNull() {
        val securityPackageManager = mockk<PackageManager>()
        every { securityPackageManager.getLaunchIntentForPackage(any()) } throws SecurityException("Denied")
        assertNull(NotificationParsingHelper(securityPackageManager).safeLaunchIntentUri("com.example.app"))

        val invalidPackageManager = mockk<PackageManager>()
        every { invalidPackageManager.getLaunchIntentForPackage(any()) } throws IllegalArgumentException("Invalid")
        assertNull(NotificationParsingHelper(invalidPackageManager).safeLaunchIntentUri("invalid package"))
    }

    @Test
    fun safeLaunchIntentUri_noLaunchIntent_returnsNull() {
        val packageManager = mockk<PackageManager>()
        every { packageManager.getLaunchIntentForPackage("com.example.app") } returns null

        assertNull(NotificationParsingHelper(packageManager).safeLaunchIntentUri("com.example.app"))
    }

    @Test
    fun safeLaunchIntentUri_unknownPackage_returnsNull() {
        assertNull(helper.safeLaunchIntentUri("com.nonexistent.fake.app"))
    }
}
