package com.jeffers.notimindlite.tier1feature

import com.jeffers.notimindlite.data.local.NotificationReadStateQueries
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReadStateQueriesContractTest {
    @Test
    fun markAsReadBatchSkipsEmptyKeysAndDispatchesByDeletePolicy() = runBlocking {
        val queries = RecordingQueries()

        queries.markAsReadBatch(emptyList())
        assertTrue(queries.calls.isEmpty())

        queries.markAsReadBatch(listOf("read-key"))
        queries.markAsReadBatch(listOf("delete-key"), autoDeleteOnRead = true)

        assertEquals(
            listOf("mark-batch:read-key", "delete-batch:delete-key"),
            queries.calls
        )
    }

    @Test
    fun markAsReadDispatchesByDeletePolicy() = runBlocking {
        val queries = RecordingQueries()

        queries.markAsRead("read-key")
        queries.markAsRead("delete-key", autoDeleteOnRead = true)

        assertEquals(listOf("mark:read-key", "delete:delete-key"), queries.calls)
    }

    @Test
    fun markAllAsReadDispatchesByDeletePolicy() = runBlocking {
        val queries = RecordingQueries()

        queries.markAllAsRead()
        queries.markAllAsRead(autoDeleteOnRead = true)

        assertEquals(listOf("mark-all", "delete-unread"), queries.calls)
    }

    private class RecordingQueries : NotificationReadStateQueries {
        val calls = mutableListOf<String>()

        override suspend fun markAsReadBatchInternal(keys: List<String>) {
            calls += "mark-batch:${keys.joinToString()}"
        }

        override suspend fun deleteByKeys(keys: List<String>) {
            calls += "delete-batch:${keys.joinToString()}"
        }

        override suspend fun updatePinnedStatus(key: String, isPinned: Boolean) = Unit

        override suspend fun updatePinnedStatusBatch(keys: List<String>, isPinned: Boolean) = Unit

        override suspend fun markAsReadInternal(key: String) {
            calls += "mark:$key"
        }

        override suspend fun deleteByKey(key: String) {
            calls += "delete:$key"
        }

        override suspend fun markAllAsReadInternal() {
            calls += "mark-all"
        }

        override suspend fun deleteUnread() {
            calls += "delete-unread"
        }
    }
}
