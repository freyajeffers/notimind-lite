package com.jeffers.notimindlite.ui.components

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNotificationGroupContractTest {
    @Test
    fun emptyInputReturnsNoGroups() {
        assertTrue(groupNotifications(emptyList()).isEmpty())
    }

    @Test
    fun appGroupingUsesGroupKeyAndFallsBackToPackageName() {
        val items = listOf(
            notification(key = "first", packageName = "app.pkg", groupKey = null, postTime = 1),
            notification(key = "second", packageName = "app.pkg", groupKey = "app.pkg", postTime = 2),
            notification(key = "third", packageName = "app.pkg", groupKey = "custom", postTime = 3)
        )

        val groups = groupNotifications(items)

        assertEquals(listOf("custom", "app.pkg"), groups.map { it.groupKey })
        assertEquals(listOf("third"), groups[0].items.map { it.key })
        assertEquals(listOf("second", "first"), groups[1].items.map { it.key })
    }

    @Test
    fun individualGroupingUsesNotificationKeys() {
        val items = listOf(
            notification(key = "first", packageName = "app.pkg", groupKey = "same", postTime = 1),
            notification(key = "second", packageName = "app.pkg", groupKey = "same", postTime = 2)
        )

        assertEquals(
            listOf("second", "first"),
            groupNotifications(items, groupByApp = false).map { it.groupKey }
        )
    }

    @Test
    fun newestItemProvidesDisplayMetadataAndPinStateAggregatesAcrossGroup() {
        val older = notification(
            key = "older",
            packageName = "old.pkg",
            groupKey = "shared",
            appName = "Old label",
            postTime = 5,
            appIconUri = "old-icon"
        )
        val newest = notification(
            key = "newest",
            packageName = "new.pkg",
            groupKey = "shared",
            appName = "New label",
            postTime = 9,
            isPinned = true,
            appIconUri = "new-icon"
        )

        val group = groupNotifications(listOf(older, newest)).single()

        assertEquals("New label", group.appName)
        assertEquals("new.pkg", group.packageName)
        assertEquals("new-icon", group.appIconUri)
        assertEquals(9L, group.latestPostTime)
        assertTrue(group.isPinned)
        assertEquals(listOf("newest", "older"), group.items.map { it.key })
    }

    @Test
    fun sortingModesKeepPinnedGroupsAheadOfUnpinnedGroups() {
        val groups = listOf(
            notification("pinned-old", "pkg.old", "Zulu", 10, isPinned = true),
            notification("pinned-new", "pkg.new", "Alpha", 30, isPinned = true),
            notification("unpinned-mid", "pkg.mid", "Beta", 20)
        )

        assertEquals(
            listOf("pinned-old", "pinned-new", "unpinned-mid"),
            groupNotifications(groups, sortOrder = "oldest").map { it.items.single().key }
        )
        assertEquals(
            listOf("pinned-new", "pinned-old", "unpinned-mid"),
            groupNotifications(groups, sortOrder = "app").map { it.items.single().key }
        )
        assertEquals(
            listOf("pinned-new", "pinned-old", "unpinned-mid"),
            groupNotifications(groups, sortOrder = "unknown").map { it.items.single().key }
        )
        assertFalse(groupNotifications(groups, sortOrder = "oldest").last().isPinned)
    }

    private fun notification(
        key: String,
        packageName: String,
        appName: String = packageName,
        postTime: Long,
        groupKey: String? = null,
        isPinned: Boolean = false,
        appIconUri: String? = null
    ) = NotificationEntity(
        key = key,
        packageName = packageName,
        appName = appName,
        title = key,
        content = "Body",
        postTime = postTime,
        groupKey = groupKey,
        isPinned = isPinned,
        appIconUri = appIconUri
    )
}
