package com.jeffers.notimindlite.service

import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootRestoreManagerTest {
    @Test
    fun consolidateForBoot_emptyList_returnsEmptyList() = runBlocking {
        val result = BootRestoreManager.consolidateForBoot(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun consolidateForBoot_underThreshold_returnsOneToOneResults() = runBlocking {
        val notifications = (1..10).map { index ->
            createNotification(
                id = index.toLong(),
                title = "Title $index",
                content = "Content $index",
                appName = "App $index"
            )
        }

        val result = BootRestoreManager.consolidateForBoot(notifications)
        assertEquals(10, result.size)
        result.forEachIndexed { index, item ->
            val expectedId = (index + 1).toLong()
            assertEquals("Title $expectedId", item.summaryTitle)
            assertEquals("Content $expectedId", item.summaryContent)
            assertEquals("App $expectedId", item.appName)
            assertEquals(listOf(expectedId), item.originalIds)
        }
    }

    @Test
    fun consolidateForBoot_exactThreshold_returnsOneToOneResults() = runBlocking {
        val notifications = (1..45).map { index ->
            createNotification(
                id = index.toLong(),
                title = "Title $index",
                content = "Content $index"
            )
        }

        val result = BootRestoreManager.consolidateForBoot(notifications)
        assertEquals(45, result.size)
    }

    @Test
    fun consolidateForBoot_overThresholdWithDuplicateTitles_groupsByTitle() = runBlocking {
        // 46 notifications with only 3 distinct titles
        val notifications = (1..46).map { index ->
            val title = when {
                index <= 10 -> "Alert"
                index <= 20 -> "Meeting"
                else -> "Update"
            }
            createNotification(
                id = index.toLong(),
                title = title,
                content = "Message $index",
                appName = "Slack"
            )
        }

        val result = BootRestoreManager.consolidateForBoot(notifications)
        assertEquals(3, result.size)

        val alertGroup = result.first { it.summaryTitle == "[Grouped] Alert" }
        assertEquals(10, alertGroup.originalIds.size)
        assertEquals((1L..10L).toList(), alertGroup.originalIds)
        assertTrue(alertGroup.summaryContent.contains("Message 1"))
        assertTrue(alertGroup.summaryContent.contains("Message 10"))

        val meetingGroup = result.first { it.summaryTitle == "[Grouped] Meeting" }
        assertEquals(10, meetingGroup.originalIds.size)

        val updateGroup = result.first { it.summaryTitle == "[Grouped] Update" }
        assertEquals(26, updateGroup.originalIds.size)
    }

    @Test
    fun consolidateForBoot_overThresholdWithUniqueTitles_groupsByPackage() = runBlocking {
        // 50 notifications with unique titles from 2 distinct packages
        val notifications = (1..50).map { index ->
            val pkg = if (index <= 25) "com.slack" else "com.discord"
            val app = if (index <= 25) "Slack" else "Discord"
            createNotification(
                id = index.toLong(),
                packageName = pkg,
                appName = app,
                title = "Unique Title $index",
                content = "Content $index"
            )
        }

        val result = BootRestoreManager.consolidateForBoot(notifications)
        assertEquals(2, result.size)

        val slackGroup = result.first { it.appName == "Slack" }
        assertEquals("Notifications from Slack", slackGroup.summaryTitle)
        assertEquals(25, slackGroup.originalIds.size)

        val discordGroup = result.first { it.appName == "Discord" }
        assertEquals("Notifications from Discord", discordGroup.summaryTitle)
        assertEquals(25, discordGroup.originalIds.size)
    }

    private fun createNotification(
        id: Long,
        packageName: String = "com.test.app",
        appName: String = "TestApp",
        title: String = "Title",
        content: String = "Content"
    ): NotificationEntity = NotificationEntity(
        id = id,
        key = "key_$id",
        packageName = packageName,
        appName = appName,
        title = title,
        content = content,
        postTime = System.currentTimeMillis()
    )
}
