package com.jeffers.notimindlite.service

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import com.jeffers.notimindlite.util.NotificationLauncher
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationServiceHelpersTest {
    @Before
    fun setUp() {
        NotificationLauncher.clearCache()
    }

    @After
    fun tearDown() {
        NotificationLauncher.clearCache()
    }

    @Test
    fun parseNotificationPackageList_handlesMultipleDelimitersAndTrims() {
        val input = "com.slack, com.discord\ncom.telegram \t com.whatsapp  "
        val parsed = parseNotificationPackageList(input)

        val expected = setOf("com.slack", "com.discord", "com.telegram", "com.whatsapp")
        assertEquals(expected, parsed)
    }

    @Test
    fun parseNotificationPackageList_handlesEmptyAndWhitespaceOnlyStrings() {
        assertTrue(parseNotificationPackageList("").isEmpty())
        assertTrue(parseNotificationPackageList("   \n\t  ").isEmpty())
        assertTrue(parseNotificationPackageList(",,,\n\n\t").isEmpty())
    }

    @Test
    fun parseNotificationPackageList_singlePackageWithoutDelimiters() {
        val result = parseNotificationPackageList("com.example.app")
        assertEquals(setOf("com.example.app"), result)
    }

    @Test
    fun registerNotificationActions_registersAllActionsIntoNotificationLauncher() {
        val mockPendingIntent1 = mockk<PendingIntent>()
        val mockPendingIntent2 = mockk<PendingIntent>()

        val action1 = Notification.Action.Builder(null, "Action 1", mockPendingIntent1).build()
        val action2 = Notification.Action.Builder(null, "Action 2", mockPendingIntent2).build()

        val sbn = mockk<StatusBarNotification> {
            every { notification } returns mockk {
                actions = arrayOf(action1, action2)
            }
        }

        val key = "test_sbn_key_1"
        registerNotificationActions(sbn, key)

        assertEquals(2, NotificationLauncher.getActionIntentCacheSize())
        assertTrue(NotificationLauncher.isActionAvailable(key, 0))
        assertTrue(NotificationLauncher.isActionAvailable(key, 1))
    }

    @Test
    fun registerNotificationActions_nullNotificationOrActions_doesNotThrowOrRegister() {
        val sbnNoNotification = mockk<StatusBarNotification> {
            every { notification } returns null
        }
        registerNotificationActions(sbnNoNotification, "empty_key_1")
        assertEquals(0, NotificationLauncher.getActionIntentCacheSize())

        val sbnNullActions = mockk<StatusBarNotification> {
            every { notification } returns mockk {
                actions = null
            }
        }
        registerNotificationActions(sbnNullActions, "empty_key_2")
        assertEquals(0, NotificationLauncher.getActionIntentCacheSize())
    }
}
