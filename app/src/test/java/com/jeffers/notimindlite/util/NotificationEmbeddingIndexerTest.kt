package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationEmbeddingIndexerTest {
    @Test
    fun indexBatch_withSizeMismatch_skipsExecutionGracefully() = runBlocking {
        val mockDao = mockk<NotificationDao>(relaxed = true)
        val indexer = NotificationEmbeddingIndexer()
        val entities = listOf(
            NotificationEntity(
                id = 1L,
                key = "key_1",
                packageName = "com.test",
                appName = "App",
                title = "Title",
                content = "Content"
            )
        )
        // Pass 2 IDs for 1 entity -> size mismatch
        indexer.indexBatch(mockDao, listOf(1L, 2L), entities)

        coVerify(exactly = 0) { mockDao.updateEmbedding(any(), any()) }
    }

    @Test
    fun indexBatch_withEmptyLists_completesImmediatelyWithoutInvocation() = runBlocking {
        val mockDao = mockk<NotificationDao>(relaxed = true)
        val indexer = NotificationEmbeddingIndexer()

        indexer.indexBatch(mockDao, emptyList(), emptyList())

        coVerify(exactly = 0) { mockDao.updateEmbedding(any(), any()) }
    }

    @Test
    fun indexSingle_withValidEntity_computesEmbeddingAndUpdatesDao() = runBlocking {
        val mockDao = mockk<NotificationDao>(relaxed = true)
        val indexer = NotificationEmbeddingIndexer()
        val entity = NotificationEntity(
            id = 42L,
            key = "key_42",
            packageName = "com.banking.secure",
            appName = "Bank",
            title = "Security Alert",
            content = "Wire transfer authorization needed",
            subText = "Account 1234",
            bigText = "Please confirm the transaction",
            category = "alert"
        )

        indexer.indexSingle(mockDao, entity)

        coVerify(exactly = 1) { mockDao.updateEmbedding(42L, any()) }
    }

    @Test
    fun indexBatch_withMatchingIdsAndEntities_updatesAllEmbeddings() = runBlocking {
        val mockDao = mockk<NotificationDao>(relaxed = true)
        val indexer = NotificationEmbeddingIndexer()
        val entities = listOf(
            NotificationEntity(
                id = 1L,
                key = "k1",
                packageName = "com.app.a",
                appName = "AppA",
                title = "Title A",
                content = "Content A"
            ),
            NotificationEntity(
                id = 2L,
                key = "k2",
                packageName = "com.app.b",
                appName = "AppB",
                title = "Title B",
                content = "Content B"
            )
        )

        indexer.indexBatch(mockDao, listOf(100L, 200L), entities)

        coVerify(exactly = 1) { mockDao.updateEmbedding(100L, any()) }
        coVerify(exactly = 1) { mockDao.updateEmbedding(200L, any()) }
    }

    @Test
    fun indexSingle_databaseException_isHandledGracefullyWithoutCrashing() = runBlocking {
        val mockDao = mockk<NotificationDao>()
        io.mockk.coEvery { mockDao.updateEmbedding(any(), any()) } throws
            android.database.sqlite.SQLiteException("Simulated SQLite write error")

        val indexer = NotificationEmbeddingIndexer()
        val entity = NotificationEntity(
            id = 99L,
            key = "key_err",
            packageName = "com.test.err",
            appName = "ErrApp",
            title = "Test",
            content = "Body"
        )

        // Should not throw SQLiteException
        indexer.indexSingle(mockDao, entity)
        coVerify(exactly = 1) { mockDao.updateEmbedding(99L, any()) }
    }
}
