package com.notimind.lite.tier3_pairwise

import android.content.Context
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
import com.notimind.lite.base.BaseRobolectricTest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncIntegrationPairwiseTest : BaseRobolectricTest() {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: NotificationDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var repository: FirestoreSyncRepository
    private val userId = "test_user_pairwise"

    @Before
    fun setup() {
        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        mockFirestore = mockk()
        
        every { mockDb.notificationDao() } returns mockDao
        repository = FirestoreSyncRepository(mockDb, mockFirestore)
    }

    @Test
    fun `sync should preserve local changes when local version is newer (LWW)`() = runBlocking {
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
        
        every { mockDao.getNotificationByKey(key) } returns localNotif
        every { mockDao.getUnsyncedNotifications() } returns emptyList()
        
        val mockCol = mockk<CollectionReference>()
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.get() } returns mockk { every { await() } returns mockSnapshot }
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 1000L // Remote is older
        
        repository.sync(userId, mockk())
        
        verify(exactly = 0) { mockDao.insertNotification(any()) }
    }

    @Test
    fun `sync should update local record when remote version is newer (LWW)`() = runBlocking {
        val key = "conflict_remote_newer"
        val localNotif = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Local Title",
            content = "Local Content",
            lastUpdatedTime = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        
        every { mockDao.getNotificationByKey(key) } returns localNotif
        every { mockDao.getUnsyncedNotifications() } returns emptyList()
        
        val mockCol = mockk<CollectionReference>()
        val mockSnapshot = mockk<QuerySnapshot>()
        val mockDoc = mockk<QueryDocumentSnapshot>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.get() } returns mockk { every { await() } returns mockSnapshot }
        every { mockSnapshot.documents } returns listOf(mockDoc)
        every { mockDoc.getString("key") } returns key
        every { mockDoc.getLong("lastUpdatedTime") } returns 2000L // Remote is newer
        every { mockDoc.getString("title") } returns "Remote Title"
        every { mockDoc.getString("content") } returns "Remote Content"
        every { mockDoc.getString("packageName") } returns "com.test"
        every { mockDoc.getString("appName") } returns "TestApp"
        every { mockDoc.getLong("postTime") } returns 500L
        
        repository.sync(userId, mockk())
        
        verify { 
            mockDao.insertNotification(withArg {
                assertEquals("Remote Title", it.title)
                assertEquals("Remote Content", it.content)
            }) 
        }
    }

    @Test
    fun `sync should NOT delete remote record when local status is PENDING_DELETE (Retention Policy)`() = runBlocking {
        val key = "retention_test_key"
        val notification = NotificationEntity(
            key = key,
            packageName = "com.test",
            appName = "TestApp",
            title = "Delete Me",
            content = "Content",
            syncStatus = SyncStatus.PENDING_DELETE
        )
        
        every { mockDao.getUnsyncedNotifications() } returns listOf(notification)
        
        val mockCol = mockk<CollectionReference>()
        val mockDoc = mockk<DocumentReference>()
        
        every { mockFirestore.collection("users").document(userId).collection("notifications") } returns mockCol
        every { mockCol.document(key) } returns mockDoc
        
        repository.sync(userId, mockk())
        
        // Verify delete was NEVER called on Firestore
        verify(exactly = 0) { mockDoc.delete() }
        // Verify local status was updated to SYNCED (marking as "processed")
        verify { mockDao.updateSyncStatus(key, SyncStatus.SYNCED) }
    }
}

private fun <T> assertEquals(expected: T, actual: T, message: String) {
    if (expected != actual) {
        throw AssertionError("$message: Expected <$expected> but was <$actual>")
    }
}
