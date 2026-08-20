package com.notimind.lite.tier2_boundary

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirestoreSyncBoundaryTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: com.google.firebase.firestore.FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository

    @Before
    fun setup() {
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()
        
        every { mockDb.notificationDao() } returns mockDao
        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @Test
    fun `sync should handle Firestore unavailability gracefully`() = runBlocking {
        val userId = "test_user"
        val notification = NotificationEntity(
            key = "n1", packageName = "p1", appName = "a1", title = "t1", content = "c1",
            syncStatus = SyncStatus.PENDING_UPLOAD
        )

        every { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        
        val mockCol = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockDoc = mockk<com.google.firebase.firestore.DocumentReference>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.document(notification.key) } returns mockDoc
        
        // Simulate Firestore exception (e.g. UNAVAILABLE)
        every { mockDoc.set(any()) } throws FirebaseFirestoreException(
            FirebaseFirestoreException.Code.UNAVAILABLE, "Service unavailable", null
        )

        val result = repository.sync(userId, javax.crypto.spec.SecretKeySpec("1234567890123456".toByteArray(), "AES"))

        assertTrue("Sync should return failure when Firestore is unavailable", result.isFailure)
        verify(exactly = 0) { mockDao.updateSyncStatus(any(), SyncStatus.SYNCED) }
    }

    @Test
    fun `sync should handle corrupted remote documents without crashing`() = runBlocking {
        val userId = "test_user"
        
        val mockCol = mockk<com.google.firebase.firestore.CollectionReference>()
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.get() } returns mockk {
            every { await() } returns mockSnapshot
        }
        every { mockSnapshot.documents } returns listOf(mockDoc)
        
        // Simulate corrupted data: missing required 'key' field
        every { mockDoc.getString("key") } returns null
        every { mockDoc.getLong("lastUpdatedTime") } returns 1000L

        val result = repository.sync(userId, javax.crypto.spec.SecretKeySpec("1234567890123456".toByteArray(), "AES"))

        assertTrue("Sync should complete successfully even if some remote docs are corrupted", result.isSuccess)
        verify(exactly = 0) { mockDao.insertNotification(any()) }
    }

    @Test
    fun `sync should handle Firestore quota exceeded`() = runBlocking {
        val userId = "test_user"
        
        val mockCol = mockk<com.google.firebase.firestore.CollectionReference>()
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.get() } throws FirebaseFirestoreException(
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED, "Quota exceeded", null
        )

        val result = repository.sync(userId, javax.crypto.spec.SecretKeySpec("1234567890123456".toByteArray(), "AES"))

        assertTrue("Sync should return failure when quota is exceeded", result.isFailure)
    }
}
