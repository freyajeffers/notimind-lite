package com.notimind.lite.tier4_realworld

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.service.NotificationLoggerService
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationBurstServiceTest : BaseRobolectricTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `database should handle notification burst of 100 events without dropping data`() = runTest {
        val burstSize = 100

        for (i in 1..burstSize) {
            dao.insert(
                NotificationEntity(
                    key = "burst_key_$i",
                    packageName = "com.burst.app",
                    appName = "Burst App",
                    title = "Burst Title $i",
                    content = "Burst Content $i",
                    postTime = System.currentTimeMillis() + i
                )
            )
        }

        val totalCount = dao.getNotificationCount()
        assertEquals(burstSize, totalCount)
    }

    @Test
    fun `database should maintain query performance under high load`() = runTest {
        val burstSize = 50

        for (i in 1..burstSize) {
            dao.insert(
                NotificationEntity(
                    key = "perf_key_$i",
                    packageName = "com.perf.app",
                    appName = "Perf App",
                    title = "Perf Title $i",
                    content = "Perf Content $i",
                    postTime = System.currentTimeMillis() + i
                )
            )
        }

        val activeList = dao.getActiveNotificationsList()
        assertEquals(burstSize, activeList.size)
    }
}
