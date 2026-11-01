package com.jeffers.notimindlite.tier1feature

import com.jeffers.notimindlite.data.auth.UserSession
import com.jeffers.notimindlite.data.local.AppEntity
import com.jeffers.notimindlite.data.local.AppWithGroupsAndNotifications
import com.jeffers.notimindlite.data.local.AppWithNotifications
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.NotificationFtsEntity
import com.jeffers.notimindlite.data.local.NotificationGroupEntity
import com.jeffers.notimindlite.data.local.NotificationGroupWithChildren
import com.jeffers.notimindlite.data.local.NotificationWithGroupAndApp
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.domain.entity.Resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainValueContractCoverageTest {
    @Test
    fun userSessionDefaultsAndCopiesAllAuthenticationFields() {
        val defaults = UserSession()
        assertNull(defaults.uid)
        assertNull(defaults.email)
        assertNull(defaults.displayName)
        assertNull(defaults.photoUrl)
        assertFalse(defaults.isAuthenticated)
        assertFalse(defaults.isAuthenticating)
        assertNull(defaults.error)

        val authenticated = UserSession(
            uid = "user-1",
            email = "user@example.invalid",
            displayName = "Example User",
            photoUrl = "https://example.invalid/avatar",
            isAuthenticated = true,
            isAuthenticating = true,
            error = "previous error"
        )
        val copy = authenticated.copy(isAuthenticating = false, error = null)

        assertEquals("user-1", copy.uid)
        assertEquals("user@example.invalid", copy.email)
        assertEquals("Example User", copy.displayName)
        assertEquals("https://example.invalid/avatar", copy.photoUrl)
        assertTrue(copy.isAuthenticated)
        assertFalse(copy.isAuthenticating)
        assertNull(copy.error)
        assertEquals(authenticated, authenticated.copy())
        assertEquals(authenticated.hashCode(), authenticated.copy().hashCode())
    }

    @Test
    fun resourceVariantsRetainTheirPayloadsAndValueSemantics() {
        val success = Resource.Success("loaded")
        assertEquals("loaded", success.data)
        assertEquals(success, Resource.Success("loaded"))

        val cause = IllegalStateException("unavailable")
        val error = Resource.Error("request failed", cause)
        assertEquals("request failed", error.message)
        assertSame(cause, error.throwable)
        assertNull(Resource.Error("request failed").throwable)
        assertSame(Resource.Loading, Resource.Loading)
    }

    @Test
    fun appEntityDefaultsAndCopiesPreserveSyncMetadata() {
        val defaults = AppEntity(packageName = "com.example.app", appName = "Example")
        assertNull(defaults.appIconUri)
        assertEquals(0, defaults.statusBarIconRes)
        assertNull(defaults.statusBarIconPackage)
        assertEquals(SyncStatus.PENDING_UPLOAD, defaults.syncStatus)
        assertEquals(0L, defaults.lastSyncedAt)

        val original = defaults.copy(
            appIconUri = "content://icons/example",
            statusBarIconRes = 41,
            statusBarIconPackage = "com.example.icons",
            firstSeenTime = 10L,
            lastSeenTime = 20L,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = 30L
        )
        val updated = original.copy(appName = "Renamed")

        assertEquals("com.example.app", updated.packageName)
        assertEquals("Renamed", updated.appName)
        assertEquals("content://icons/example", updated.appIconUri)
        assertEquals(41, updated.statusBarIconRes)
        assertEquals("com.example.icons", updated.statusBarIconPackage)
        assertEquals(10L, updated.firstSeenTime)
        assertEquals(20L, updated.lastSeenTime)
        assertEquals(SyncStatus.SYNCED, updated.syncStatus)
        assertEquals(30L, updated.lastSyncedAt)
        assertEquals(original, original.copy())
    }

    @Test
    fun notificationGroupEntityDefaultsAndCopiesPreserveState() {
        val defaults = NotificationGroupEntity(
            groupKey = "group-1",
            packageName = "com.example.app",
            appName = "Example"
        )
        assertNull(defaults.appIconUri)
        assertEquals(1L, defaults.notificationCount.toLong())
        assertEquals(1L, defaults.activeCount.toLong())
        assertFalse(defaults.isPinned)
        assertFalse(defaults.isDismissed)
        assertNull(defaults.summaryTitle)
        assertNull(defaults.summaryText)

        val original = defaults.copy(
            appIconUri = "content://icons/example",
            latestPostTime = 100L,
            notificationCount = 4,
            activeCount = 2,
            isPinned = true,
            isDismissed = true,
            summaryTitle = "Summary",
            summaryText = "Details",
            lastUpdatedTime = 200L
        )
        val updated = original.copy(isDismissed = false)

        assertEquals("group-1", updated.groupKey)
        assertEquals("com.example.app", updated.packageName)
        assertEquals("Example", updated.appName)
        assertEquals("content://icons/example", updated.appIconUri)
        assertEquals(100L, updated.latestPostTime)
        assertEquals(4L, updated.notificationCount.toLong())
        assertEquals(2L, updated.activeCount.toLong())
        assertTrue(updated.isPinned)
        assertFalse(updated.isDismissed)
        assertEquals("Summary", updated.summaryTitle)
        assertEquals("Details", updated.summaryText)
        assertEquals(200L, updated.lastUpdatedTime)
        assertEquals(original, original.copy())
    }

    @Test
    fun roomRelationModelsRetainTheirRelatedEntities() {
        val app = AppEntity(packageName = "com.example.app", appName = "Example")
        val group = NotificationGroupEntity(
            groupKey = "group-1",
            packageName = app.packageName,
            appName = app.appName
        )
        val notification = NotificationEntity(
            key = "notification-1",
            packageName = app.packageName,
            appName = app.appName,
            title = "Title",
            content = "Body",
            groupKey = group.groupKey
        )
        val children = listOf(notification)

        val appNotifications = AppWithNotifications(app, children)
        val groupChildren = NotificationGroupWithChildren(group, children)
        val appGroups = AppWithGroupsAndNotifications(app, listOf(group), children)
        val notificationParents = NotificationWithGroupAndApp(notification, group, app)
        val fts = NotificationFtsEntity("Title", "Body", "Example", "com.example.app")

        assertEquals(app, appNotifications.app)
        assertEquals(children, appNotifications.notifications)
        assertEquals(group, groupChildren.group)
        assertEquals(children, groupChildren.notifications)
        assertEquals(listOf(group), appGroups.groups)
        assertEquals(children, appGroups.notifications)
        assertEquals(notification, notificationParents.notification)
        assertEquals(group, notificationParents.group)
        assertEquals(app, notificationParents.app)
        assertEquals("Title", fts.title)
        assertEquals("Body", fts.content)
        assertEquals("Example", fts.appName)
        assertEquals("com.example.app", fts.packageName)
        assertEquals(fts, fts.copy())
    }
}
