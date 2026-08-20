package com.notimind.lite.tier4_realworld

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudChaosStabilityTest : BaseRobolectricTest() {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository
    private val userId = "chaos_user"

    @Before
    fun setup() {
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()
        
        every { mockDb.notificationDao() } returns mockDao
        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @Test
    fun `sync should return failure and NOT update local status on mid-upload network drop`() = runBlocking {
        val notification = NotificationEntity(
            key = "chaos_1",
            packageName = "com.chaos",
            appName = "ChaosApp",
            title = "T1",
            content = "C1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        
        every { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        
        val mockCol = mockk<CollectionReference>()
        val mockDoc = mockk<DocumentReference>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.document(notification.key) } returns mockDoc
        
        // Simulate network failure during the set() call
        every { mockDoc.set(any()) } throws FirebaseFirestoreException(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            "Network connection lost mid-upload",
            null
        )
        
        val result = repository.sync(userId, mockk())
        
        assertTrue("Sync should report failure when network drops", result.isFailure)
        verify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED) }
    }

    @Test
    fun `sync should return failure when Firestore rate limit is exceeded`() = runBlocking {
        val notification = NotificationEntity(
            key = "rate_1",
            packageName = "com.rate",
            appName = "RateApp",
            title = "T1",
            content = "C1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
        
        every { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        
        val mockCol = mockk<CollectionReference>()
        val mockDoc = mockk<DocumentReference>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.document(notification.key) } returns mockDoc
        
        every { mockDoc.set(any()) } throws FirebaseFirestoreException(
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            "Quota exceeded",
            null
        )
        
        val result = repository.sync(userId, mockk())
        
        assertTrue("Sync should report failure when rate limited", result.isFailure)
        verify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED) }
    }

    @Test
    fun `sync should handle large remote batches without blocking main thread`() = runBlocking {
        val mockCol = mockk<CollectionReference>()
        val mockSnapshot = mockk<QuerySnapshot>()
        
        // Create 100 remote documents
        val mockDocs = List(100) { i ->
            mockk<QueryDocumentSnapshot> {
                every { getString("key") } returns "remote_$i"
                every { getLong("lastUpdatedTime") } returns 1000L + i
                every { getString("title") } returns "Title $i"
                every { getString("content") } returns "Content $i"
                every { getString("packageName") } returns "com.remote"
                every { getString("appName") } returns "RemoteApp"
                every { getLong("postTime") } returns 500L
            }
        }
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.get() } returns mockk { every { await() } returns mockSnapshot }
        every { mockSnapshot.documents } returns mockDocs
        every { mockDao.getUnsyncedNotifications() } returns emptyList()
        
        val result = repository.sync(userId, mockk())
        
        assertTrue("Sync should complete successfully for large batches", result.isSuccess)
        verify(exactly = 100) { mockDao.insertNotification(any()) }
    }
}
