package com.notimind.lite.tier4_realworld

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CloudChaosStabilityTest : BaseRobolectricTest() {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository
    private val userId = "chaos_user"
    private val testKey = SecretKeySpec("1234567890123456".toByteArray(), "AES")

    @Before
    override fun setup() {
        super.setup()
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockDb.notificationDao() } returns mockDao
        every { mockFirestore.batch() } returns mockBatch
        every { mockBatch.commit() } returns Tasks.forResult(null)
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
    fun sync_should_return_failure_and_NOT_update_local_status_on_mid_upload_network_drop() = runTest {
        val notification = NotificationEntity(
            key = "chaos_1",
            packageName = "com.chaos",
            appName = "ChaosApp",
            title = "T1",
            content = "C1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)

        val mockCol = mockCollection()
        val mockDoc = mockk<DocumentReference>()
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockFirestore.batch() } returns mockBatch
        every { mockCol.document(notification.key) } returns mockDoc
        every { mockBatch.commit() } returns Tasks.forException(
            FirebaseFirestoreException("Network connection lost mid-upload", FirebaseFirestoreException.Code.UNAVAILABLE)
        )

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should report failure when network drops", result.isFailure)
        coVerify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED, any()) }
        coVerify(exactly = 0) { mockDao.updateSyncStatusBatch(any(), SyncStatus.SYNCED, any()) }
    }

    @Test
    fun sync_should_return_failure_when_Firestore_rate_limit_is_exceeded() = runTest {
        val notification = NotificationEntity(
            key = "rate_1",
            packageName = "com.rate",
            appName = "RateApp",
            title = "T1",
            content = "C1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)

        val mockCol = mockCollection()
        val mockDoc = mockk<DocumentReference>()
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockFirestore.batch() } returns mockBatch
        every { mockCol.document(notification.key) } returns mockDoc
        every { mockBatch.commit() } returns Tasks.forException(
            FirebaseFirestoreException("Quota exceeded", FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)
        )

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should report failure when rate limited", result.isFailure)
        coVerify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED, any()) }
        coVerify(exactly = 0) { mockDao.updateSyncStatusBatch(any(), SyncStatus.SYNCED, any()) }
    }

    @Test
    fun sync_should_handle_large_remote_batches_without_blocking_main_thread() = runTest {
        val mockCol = mockCollection()
        val mockSnapshot = mockk<QuerySnapshot>()

        val mockDocs = List(100) { i ->
            mockk<QueryDocumentSnapshot> {
                every { getString("key") } returns "remote_$i"
                every { getLong("lastUpdatedTime") } returns 1000L + i
                every { getString("title") } returns SyncEncryptionHelper.encrypt("Title $i", testKey)
                every { getString("content") } returns SyncEncryptionHelper.encrypt("Content $i", testKey)
                every { getString("packageName") } returns "com.remote"
                every { getString("appName") } returns "RemoteApp"
                every { getLong("postTime") } returns 500L
                every { getLong("updateCount") } returns 1L
                every { getBoolean("isDismissed") } returns false
                every { getBoolean("isPersistent") } returns false
                every { getBoolean("isRead") } returns false
                every { getBoolean("isGroupSummary") } returns false
                every { getLong("priority") } returns 0L
                every { getBoolean("isOngoing") } returns false
                every { getBoolean("isClearable") } returns true
                every { getLong("actionsCount") } returns 0L
                every { getBoolean("isPinned") } returns false
                every { getLong("smallIconRes") } returns 0L
                every { getString("category") } returns null
                every { getString("channelId") } returns null
                every { getString("subText") } returns null
                every { getString("bigText") } returns null
                every { getString("inboxLinesJson") } returns null
                every { getString("intentUri") } returns null
                every { getString("actionLabels") } returns null
                every { getString("groupKey") } returns null
                every { getString("appIconUri") } returns null
                every { getLong("dismissReason") } returns null
                every { getLong("dismissTime") } returns null
            }
        }

        every { mockCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns mockDocs
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should complete successfully for large batches. Failure: ${result.exceptionOrNull()?.message}", result.isSuccess)
        coVerify(exactly = 1) { mockDao.insertNotifications(any()) }
    }
}
