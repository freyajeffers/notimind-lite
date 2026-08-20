package com.jeffers.notimindlite.data.sync

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.local.generateBackupKey
import com.jeffers.notimindlite.util.SyncEncryptionHelper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirestoreSyncRepositoryTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockUserCol: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockNotifCol: CollectionReference
    private lateinit var repository: FirestoreSyncRepository
    private lateinit var testSecretKey: SecretKey

    @Before
    fun setup() {
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()
        mockUserCol = mockk()
        mockUserDoc = mockk()
        mockNotifCol = mockk()
        testSecretKey = generateBackupKey()

        every { mockDb.notificationDao() } returns mockDao
        every { mockFirestore.collection("users") } returns mockUserCol
        every { mockUserCol.document(any()) } returns mockUserDoc
        every { mockUserDoc.collection("notifications") } returns mockNotifCol

        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @Test
    fun `sync should upload pending notifications and update status`() = runBlocking {
        val userId = "test_user"
        val notification = NotificationEntity(
            key = "notif_1",
            packageName = "com.test",
            appName = "TestApp",
            title = "Hello",
            content = "World",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        val mockDoc = mockk<DocumentReference>()
        every { mockNotifCol.document(notification.key) } returns mockDoc
        every { mockDoc.set(any()) } returns Tasks.forResult(null)

        val mockSnapshot = mockk<QuerySnapshot>()
        every { mockSnapshot.documents } returns emptyList()
        every { mockNotifCol.get() } returns Tasks.forResult(mockSnapshot)

        val result = repository.sync(userId, testSecretKey)

        assertTrue(result.isSuccess)
        coVerify { mockDao.updateSyncStatus("notif_1", SyncStatus.SYNCED, any()) }
    }

    @Test
    fun `sync should preserve records and update status when marked PENDING_DELETE`() = runBlocking {
        val userId = "test_user"
        val notification = NotificationEntity(
            key = "notif_del",
            packageName = "com.test",
            appName = "TestApp",
            title = "Delete me",
            content = "...",
            syncStatus = SyncStatus.PENDING_DELETE
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        val mockSnapshot = mockk<QuerySnapshot>()
        every { mockSnapshot.documents } returns emptyList()
        every { mockNotifCol.get() } returns Tasks.forResult(mockSnapshot)

        val result = repository.sync(userId, testSecretKey)

        assertTrue(result.isSuccess)
        coVerify { mockDao.updateSyncStatus("notif_del", SyncStatus.SYNCED, any()) }
    }

    @Test
    fun `sync should resolve conflicts using Last Write Wins`() = runBlocking {
        val userId = "test_user"
        val key = "conflict_1"

        // 1. Local version (older)
        val localNotif = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Local Title",
            content = "Local Content",
            lastUpdatedTime = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        coEvery { mockDao.getNotificationByKey(key) } returns localNotif
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        // 2. Remote version (newer)
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<DocumentSnapshot>()

        every { mockNotifCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.id } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 2000L
        every { mockDoc.getLong("postTime") } returns 500L
        every { mockDoc.getString("title") } returns SyncEncryptionHelper.encrypt("Remote Title", testSecretKey)
        every { mockDoc.getString("content") } returns SyncEncryptionHelper.encrypt("Remote Content", testSecretKey)
        every { mockDoc.getString("packageName") } returns "com.test"
        every { mockDoc.getString("appName") } returns "TestApp"
        every { mockDoc.getLong("updateCount") } returns 1L
        every { mockDoc.getBoolean("isDismissed") } returns false
        every { mockDoc.getBoolean("isPersistent") } returns false
        every { mockDoc.getBoolean("isRead") } returns false
        every { mockDoc.getBoolean("isGroupSummary") } returns false
        every { mockDoc.getString("category") } returns null
        every { mockDoc.getString("channelId") } returns null
        every { mockDoc.getString("subText") } returns null
        every { mockDoc.getString("bigText") } returns null
        every { mockDoc.getString("inboxLinesJson") } returns null
        every { mockDoc.getLong("priority") } returns 0L
        every { mockDoc.getString("groupKey") } returns null
        every { mockDoc.getBoolean("isOngoing") } returns false
        every { mockDoc.getBoolean("isClearable") } returns true
        every { mockDoc.getLong("actionsCount") } returns 0L
        every { mockDoc.getLong("dismissReason") } returns null
        every { mockDoc.getLong("dismissTime") } returns null
        every { mockDoc.getString("intentUri") } returns null
        every { mockDoc.getBoolean("isPinned") } returns false
        every { mockDoc.getString("actionLabels") } returns null
        every { mockDoc.getLong("smallIconRes") } returns 0L
        every { mockDoc.getString("appIconUri") } returns null

        val result = repository.sync(userId, testSecretKey)

        assertTrue(result.isSuccess)
        coVerify {
            mockDao.insertNotification(withArg {
                assertEquals("Remote Title", it.title)
                assertEquals("Remote Content", it.content)
            })
        }
    }

    @Test
    fun `sync should preserve local changes if remote is older`() = runBlocking {
        val userId = "test_user"
        val key = "conflict_2"

        // 1. Local version (newer)
        val localNotif = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Local Title",
            content = "Local Content",
            lastUpdatedTime = 3000L,
            syncStatus = SyncStatus.SYNCED
        )
        coEvery { mockDao.getNotificationByKey(key) } returns localNotif
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        // 2. Remote version (older)
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<DocumentSnapshot>()

        every { mockNotifCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.id } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 2000L
        every { mockDoc.getLong("postTime") } returns 500L

        val result = repository.sync(userId, testSecretKey)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mockDao.insertNotification(any()) }
    }

    @Test
    fun `purgeUserData should be rejected and return failure to prevent deletion`() = runBlocking {
        val userId = "test_user"
        val result = repository.purgeUserData(userId)
        assertFalse(result.isSuccess)
    }
}
