package com.notimind.lite.tier3_pairwise

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.jeffers.notimindlite.util.SyncEncryptionHelper
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SyncIntegrationPairwiseTest : BaseRobolectricTest() {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository
    private val userId = "test_user_pairwise"
    private val testKey = SecretKeySpec("1234567890123456".toByteArray(), "AES")

    @Before
    override fun setup() {
        super.setup()
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()

        every { mockDb.notificationDao() } returns mockDao
        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @After
    override fun teardown() {
        unmockkAll()
        super.teardown()
    }

    private fun mockCollection(): CollectionReference {
        val mockCol = mockk<CollectionReference>()
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        return mockCol
    }

    @Test
    fun sync_should_preserve_local_changes_when_local_version_is_newer() = runTest {
        val key = "conflict_local_newer"
        val localNotif = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Local Title",
            content = "Local Content",
            lastUpdatedTime = 2000L,
            syncStatus = SyncStatus.SYNCED
        )

        coEvery { mockDao.getNotificationByKey(key) } returns localNotif
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        val mockCol = mockCollection()
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>()

        every { mockCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 1000L // Remote is older

        repository.sync(userId, testKey)

        coVerify(exactly = 0) { mockDao.insertNotification(any()) }
    }

    @Test
    fun sync_should_update_local_record_when_remote_version_is_newer() = runTest {
        val key = "conflict_remote_newer"
        val localNotif = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Local Title",
            content = "Local Content",
            postTime = 100L,
            lastUpdatedTime = 1000L,
            syncStatus = SyncStatus.SYNCED
        )

        coEvery { mockDao.getNotificationByKey(key) } returns localNotif
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        val mockCol = mockCollection()
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>()

        every { mockCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 2000L // Remote is newer
        every { mockDoc.getString("title") } returns SyncEncryptionHelper.encrypt("Remote Title", testKey)
        every { mockDoc.getString("content") } returns SyncEncryptionHelper.encrypt("Remote Content", testKey)
        every { mockDoc.getString("packageName") } returns "com.test"
        every { mockDoc.getString("appName") } returns "TestApp"
        every { mockDoc.getLong("postTime") } returns 500L
        every { mockDoc.getLong("updateCount") } returns 1L
        every { mockDoc.getBoolean("isDismissed") } returns false
        every { mockDoc.getBoolean("isPersistent") } returns false
        every { mockDoc.getBoolean("isRead") } returns false
        every { mockDoc.getBoolean("isGroupSummary") } returns false
        every { mockDoc.getLong("priority") } returns 0L
        every { mockDoc.getBoolean("isOngoing") } returns false
        every { mockDoc.getBoolean("isClearable") } returns true
        every { mockDoc.getLong("actionsCount") } returns 0L
        every { mockDoc.getBoolean("isPinned") } returns false
        every { mockDoc.getLong("smallIconRes") } returns 0L
        every { mockDoc.getString("category") } returns null
        every { mockDoc.getString("channelId") } returns null
        every { mockDoc.getString("subText") } returns null
        every { mockDoc.getString("bigText") } returns null
        every { mockDoc.getString("inboxLinesJson") } returns null
        every { mockDoc.getString("intentUri") } returns null
        every { mockDoc.getString("actionLabels") } returns null
        every { mockDoc.getString("groupKey") } returns null
        every { mockDoc.getString("appIconUri") } returns null
        every { mockDoc.getLong("dismissReason") } returns null
        every { mockDoc.getLong("dismissTime") } returns null

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should succeed. Result: $result", result.isSuccess)
        coVerify {
            mockDao.insertNotification(withArg {
                assertEquals("Remote Title", it.title)
                assertEquals("Remote Content", it.content)
            })
        }
    }

    @Test
    fun sync_should_NOT_delete_remote_record_when_local_status_is_PENDING_DELETE() = runTest {
        val key = "retention_test_key"
        val notification = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Delete Me",
            content = "Content",
            syncStatus = SyncStatus.PENDING_DELETE
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)

        val mockCol = mockCollection()
        val mockDoc = mockk<DocumentReference>()
        val mockSnapshot = mockk<QuerySnapshot>()

        every { mockCol.document(key) } returns mockDoc
        every { mockCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns emptyList()

        repository.sync(userId, testKey)

        coVerify(exactly = 0) { mockDoc.delete() }
        coVerify { mockDao.updateSyncStatus(key, SyncStatus.SYNCED, any()) }
    }
}
