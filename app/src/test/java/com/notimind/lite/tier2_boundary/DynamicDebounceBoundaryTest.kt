package com.notimind.lite.tier2_boundary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Robolectric

class DynamicDebounceBoundaryTest : BaseRobolectricTest() {

    @Test
    fun tc_DEB_001_smart30sDynamicDebounceValidation() = runTest {
        val serviceController = Robolectric.buildService(NotificationLoggerService::class.java)
        val service = serviceController.create().get()

        val key = "com.chat.app|1001|null|10001"
        val sbn1 = createMockStatusBarNotification(
            key = key,
            packageName = "com.chat.app",
            title = "Alice",
            text = "Hello!"
        )

        // First post: should insert into DB
        service.onNotificationPosted(sbn1)
        Thread.sleep(100) // Allow async scope launch

        var allNotifs = dao.getAllNotifications().first()
        assertEquals("First notification post should be logged", 1, allNotifs.size)

        // Second post: identical key, title, text within 30s -> should be debounced (suppressed)
        val sbnDuplicate = createMockStatusBarNotification(
            key = key,
            packageName = "com.chat.app",
            title = "Alice",
            text = "Hello!"
        )
        service.onNotificationPosted(sbnDuplicate)
        Thread.sleep(100)

        allNotifs = dao.getAllNotifications().first()
        assertEquals("Duplicate identical notification within 30s must be suppressed", 1, allNotifs.size)

        // Third post: same key, but content updated ("Hello again!") -> should BYPASS debounce instantly
        val sbnUpdated = createMockStatusBarNotification(
            key = key,
            packageName = "com.chat.app",
            title = "Alice",
            text = "Hello again!"
        )
        service.onNotificationPosted(sbnUpdated)
        Thread.sleep(100)

        allNotifs = dao.getAllNotifications().first()
        assertEquals("Updated content notification must bypass debounce instantly", 2, allNotifs.size)
        val latest = dao.getActiveNotificationsList()[0]
        assertEquals("Hello again!", latest.content)

        serviceController.destroy()
    }
}
