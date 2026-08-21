package com.notimind.lite.tier4_realworld

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.data.local.SyncStatus
import com.jeffers.notimindlite.data.local.generateBackupKey
import com.jeffers.notimindlite.data.sync.FirestoreSyncRepository
import android.database.sqlite.SQLiteFullException
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChaosStabilityTest {

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
    fun `sync should handle network instability gracefully`() = runBlocking {
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
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockFirestore.batch() } returns mockBatch
        every { mockNotifCol.document(notification.key) } returns mockDoc

        val failedTask = Tasks.forException<Void>(
            FirebaseFirestoreException("Network unstable", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        every { mockBatch.commit() } returns failedTask

        val result = repository.sync(userId, testSecretKey)

        assertTrue("Sync should return failure on network error", result.isFailure)
    }

    @Test
    fun `sync should handle database error gracefully`() = runBlocking {
        val userId = "test_user"
        coEvery { mockDao.getUnsyncedNotifications() } throws SQLiteFullException("Disk full")

        val result = repository.sync(userId, testSecretKey)

        assertTrue("Sync should return failure on SQLiteFullException", result.isFailure)
    }
}
