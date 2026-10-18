package com.jeffers.notimindlite.util

import android.database.sqlite.SQLiteException
import android.util.Log
import com.jeffers.notimindlite.data.local.Converters
import com.jeffers.notimindlite.data.local.NotificationDao
import com.jeffers.notimindlite.data.local.NotificationEntity

class NotificationEmbeddingIndexer {
    suspend fun indexSingle(dao: NotificationDao, entity: NotificationEntity) {
        try {
            val text = buildEmbeddingText(entity)
            val embedding = VectorEmbeddingHelper.computeEmbedding(text)
            dao.updateEmbedding(entity.id, Converters().fromFloatArray(embedding) ?: return)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Embedding compute skipped for ${entity.key}: invalid state", e)
        } catch (e: SQLiteException) {
            Log.w(TAG, "Embedding write skipped for ${entity.key}: database error", e)
        }
    }

    suspend fun indexBatch(dao: NotificationDao, ids: List<Long>, entities: List<NotificationEntity>) {
        if (ids.size != entities.size) {
            Log.w(TAG, "Batch embedding skipped: id/entity size mismatch (${ids.size} vs ${entities.size})")
            return
        }
        for (i in entities.indices) {
            indexSingle(dao, entities[i].copy(id = ids[i]))
        }
    }

    private fun buildEmbeddingText(entity: NotificationEntity): String = buildString {
        append(entity.appName).append(' ')
        append(entity.title).append(' ')
        append(entity.content).append(' ')
        if (!entity.subText.isNullOrEmpty()) append(entity.subText).append(' ')
        if (!entity.bigText.isNullOrEmpty()) append(entity.bigText).append(' ')
        if (!entity.category.isNullOrEmpty()) append(entity.category).append(' ')
        append(entity.packageName)
    }

    private companion object {
        const val TAG = "NotificationEmbedding"
    }
}
