package com.jeffers.notimindlite.util

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.data.local.PreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSanitizerTest {
    private lateinit var context: Context
    private lateinit var sanitizer: NotificationSanitizer
    private lateinit var preferences: PreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        preferences = PreferencesRepository(context)
        preferences.exportManager.setAnonymizeTitles(false)
        sanitizer = NotificationSanitizer(context) { pkg -> "content://icons/$pkg" }
    }

    @Test
    fun sanitizeAndBuild_withEmptyTitleAndContent_returnsNull() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.test.empty",
            title = "",
            text = ""
        )
        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNull(entity)
    }

    @Test
    fun sanitizeAndBuild_withSummaryTextPattern_returnsNull() {
        val sbnTitleSummary = createMockStatusBarNotification(
            packageName = "com.test.summary",
            title = "3 more notifications",
            text = ""
        )
        assertNull(sanitizer.sanitizeAndBuild(sbnTitleSummary, sbnTitleSummary.notification))

        val sbnContentSummary = createMockStatusBarNotification(
            packageName = "com.test.summary",
            title = "WhatsApp",
            text = "5 more notifications"
        )
        assertNull(sanitizer.sanitizeAndBuild(sbnContentSummary, sbnContentSummary.notification))
    }

    @Test
    fun sanitizeAndBuild_withStaleNotification_returnsNull() {
        val thirtyFiveDaysAgo = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000)
        val sbnStale = createMockStatusBarNotification(
            packageName = "com.test.stale",
            title = "Old Alert",
            text = "This happened a month ago",
            postTime = thirtyFiveDaysAgo
        )
        assertNull(sanitizer.sanitizeAndBuild(sbnStale, sbnStale.notification))
    }

    @Test
    fun sanitizeAndBuild_withValidNotification_returnsPopulatedEntity() {
        val now = System.currentTimeMillis()
        val sbn = createMockStatusBarNotification(
            packageName = "com.valid.app",
            title = "Meeting Reminder",
            text = "Product sync starts now",
            postTime = now
        )

        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNotNull(entity)
        assertEquals("com.valid.app", entity!!.packageName)
        assertEquals("Meeting Reminder", entity.title)
        assertEquals("Product sync starts now", entity.content)
        assertEquals("content://icons/com.valid.app", entity.appIconUri)
        assertEquals(now, entity.postTime)
        assertEquals(false, entity.isDismissed)
    }

    @Test
    fun sanitizeAndBuild_withPiiContent_redactsSensitiveFields() {
        preferences.securityManager.setRedactPii(true)
        val sbn = createMockStatusBarNotification(
            packageName = "com.banking.app",
            title = "Verification",
            text = "Your code is 123456"
        )

        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNotNull(entity)
        assertTrue(entity!!.content.contains("[REDACTED-OTP]"))
    }

    @Test
    fun sanitizeAndBuild_withAnonymizeTitlesEnabled_redactsTitle() {
        preferences.exportManager.setAnonymizeTitles(true)

        val sbn = createMockStatusBarNotification(
            packageName = "com.chat.secret",
            title = "Alice Confidential",
            text = "See you tomorrow"
        )

        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNotNull(entity)
        assertEquals("[REDACTED-TITLE]", entity!!.title)
        assertEquals("See you tomorrow", entity.content)
    }

    @Test
    fun sanitizeAndBuild_withSummarySubText_returnsNull() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.chat.app",
            title = "New messages",
            text = "Conversation update",
            subText = "2 more notifications"
        )

        assertNull(sanitizer.sanitizeAndBuild(sbn, sbn.notification))
    }

    @Test
    fun sanitizeAndBuild_withOngoingAndClearableFlags_setsAttributesProperly() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.media.player",
            title = "Playing Track",
            text = "Artist - Song",
            flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        )

        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNotNull(entity)
        assertTrue(entity!!.isOngoing)
        assertTrue(entity.isPersistent)
        assertEquals(false, entity.isClearable)
    }

    @Test
    fun sanitizeAndBuild_withGroupSummaryFlag_setsIsGroupSummary() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.group.chat",
            title = "Group Chat",
            text = "3 unread messages",
            flags = Notification.FLAG_GROUP_SUMMARY
        )

        val entity = sanitizer.sanitizeAndBuild(sbn, sbn.notification)
        assertNotNull(entity)
        assertTrue(entity!!.isGroupSummary)
    }

    private fun createMockStatusBarNotification(
        packageName: String,
        title: String,
        text: String,
        postTime: Long = System.currentTimeMillis(),
        subText: String? = null,
        flags: Int = 0
    ): StatusBarNotification {
        val notification = Notification().apply {
            this.flags = flags
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, title)
                putCharSequence(Notification.EXTRA_TEXT, text)
                if (subText != null) {
                    putCharSequence(Notification.EXTRA_SUB_TEXT, subText)
                }
            }
        }
        val constructor = StatusBarNotification::class.java.getConstructor(
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Notification::class.java,
            android.os.UserHandle::class.java,
            Long::class.javaPrimitiveType
        )
        return constructor.newInstance(
            packageName,
            packageName,
            101,
            "$packageName|101|tag|10001",
            1000,
            0,
            0,
            notification,
            Process.myUserHandle(),
            postTime
        )
    }
}
