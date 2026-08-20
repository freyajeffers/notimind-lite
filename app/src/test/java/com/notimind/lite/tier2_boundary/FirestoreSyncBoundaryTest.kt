package com.notimind.lite.tier2_boundary

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.jeffers.notimindlite.util.SyncEncryptionHelper
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
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreSyncBoundaryTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository
    private val testKey = SecretKeySpec("1234567890123456".toByteArray(), "AES")

    @Before
    fun setup() {
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()

        every { mockDb.notificationDao() } returns mockDao
        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun mockCollection(userId: String): CollectionReference {
        val mockCol = mockk<CollectionReference>()
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        return mockCol
    }

    @Test
    fun sync_should_handle_Firestore_unavailability_gracefully() = runTest {
        val userId = "test_user"
        val notification = NotificationEntity(
            key = "n1", packageName = "p1", appName = "a1", title = "t1", content = "c1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )

        coEvery { mockDao.getUnsyncedNotifications() } returns listOf(notification)

        val mockCol = mockCollection(userId)
        val mockDoc = mockk<DocumentReference>()

        every { mockCol.document(notification.key) } returns mockDoc
        every { mockDoc.set(any()) } returns Tasks.forException(
            FirebaseFirestoreException("Service unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        )

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should return failure when Firestore is unavailable", result.isFailure)
        coVerify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED, any()) }
    }

    @Test
    fun sync_should_handle_corrupted_remote_documents_without_crashing() = runTest {
        val userId = "test_user"

        val mockCol = mockCollection(userId)
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)

        every { mockCol.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.documents } returns listOf(mockDoc)

        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()

        // Simulate corrupted data: missing required 'key' field, but doc.id exists.
        // Provide valid encrypted PII so decryption doesn't crash, but keep optional encrypted fields null.
        every { mockDoc.getString("key") } returns null
        every { mockDoc.getLong("lastUpdatedTime") } returns 1000L
        every { mockDoc.id } returns "corrupt_doc"
        every { mockDoc.getString("title") } returns SyncEncryptionHelper.encrypt("Title", testKey)
        every { mockDoc.getString("content") } returns SyncEncryptionHelper.encrypt("Content", testKey)
        every { mockDoc.getString("packageName") } returns "com.test"
        every { mockDoc.getString("appName") } returns "TestApp"
        every { mockDoc.getLong("postTime") } returns 500L
        every { mockDoc.getString("category") } returns null
        every { mockDoc.getString("channelId") } returns null
        every { mockDoc.getString("subText") } returns null
        every { mockDoc.getString("bigText") } returns null
        every { mockDoc.getString("inboxLinesJson") } returns null
        every { mockDoc.getString("intentUri") } returns null
        every { mockDoc.getString("actionLabels") } returns null
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

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should complete successfully even if some remote docs are corrupted. Failure: ${result.exceptionOrNull()?.message}", result.isSuccess)
        coVerify { mockDao.insertNotification(any()) }
    }

    @Test
    fun sync_should_handle_Firestore_quota_exceeded() = runTest {
        val userId = "test_user"

        val mockCol = mockCollection(userId)
        coEvery { mockDao.getUnsyncedNotifications() } returns emptyList()
        every { mockCol.get() } returns Tasks.forException(
            FirebaseFirestoreException("Quota exceeded", FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)
        )

        val result = repository.sync(userId, testKey)

        assertTrue("Sync should return failure when quota is exceeded", result.isFailure)
    }
}
