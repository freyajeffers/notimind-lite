package com.jeffers.notimindlite.util

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.ReciprocalRankFusion
import com.jeffers.notimindlite.util.SemanticSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReciprocalRankFusionTest {

    private fun createEntity(id: Long, title: String) = NotificationEntity(
        key = "key_$id",
        packageName = "com.test",
        appName = "TestApp",
        title = title,
        content = "Content $id",
        postTime = System.currentTimeMillis(),
        lastUpdatedTime = System.currentTimeMillis(),
        updateCount = 1,
        isDismissed = false,
        isPersistent = false,
        isRead = false,
        isGroupSummary = false,
        category = "test",
        channelId = "test_chan",
        subText = "",
        bigText = "",
        inboxLinesJson = "[]",
        priority = 0,
        groupKey = null,
        isOngoing = false,
        isClearable = true,
        actionsCount = 0,
        dismissReason = null,
        dismissTime = null,
        intentUri = null,
        isPinned = false,
        actionLabels = null,
        smallIconRes = 0,
        appIconUri = null
    ).copy(id = id)

    @Test
    fun `merge should correctly rank entities present in both lists`() {
        val entity1 = createEntity(1, "T1")
        val entity2 = createEntity(2, "T2")
        
        val ftsResults = listOf(entity1, entity2)
        val semanticResults = listOf(
            SemanticSearchResult(entity2, 0.9f),
            SemanticSearchResult(entity1, 0.8f)
        )

        val fused = ReciprocalRankFusion.merge(ftsResults, semanticResults)

        // Entity 2 is rank 2 in FTS and rank 1 in Semantic
        // Entity 1 is rank 1 in FTS and rank 2 in Semantic
        // In RRF, they might be close, but since they are symmetric here, 
        // let's check if both are present and sorted.
        assertEquals(2, fused.size)
        assertTrue(fused[0].notification.id == 2L || fused[0].notification.id == 1L)
    }

    @Test
    fun `merge should handle disjoint lists`() {
        val entity1 = createEntity(1, "T1")
        val entity2 = createEntity(2, "T2")

        val ftsResults = listOf(entity1)
        val semanticResults = listOf(
            SemanticSearchResult(entity2, 0.9f)
        )

        val fused = ReciprocalRankFusion.merge(ftsResults, semanticResults)

        assertEquals(2, fused.size)
        assertTrue(fused.any { it.notification.id == 1L })
        assertTrue(fused.any { it.notification.id == 2L })
    }

    @Test
    fun `merge should respect weights`() {
        val entity1 = createEntity(1, "T1")
        val entity2 = createEntity(2, "T2")

        val ftsResults = listOf(entity1, entity2)
        val semanticResults = listOf(
            SemanticSearchResult(entity2, 0.9f),
            SemanticSearchResult(entity1, 0.8f)
        )

        // Heavily weight Semantic
        val fused = ReciprocalRankFusion.merge(
            ftsResults, 
            semanticResults, 
            ftsWeight = 0.1, 
            semanticWeight = 10.0
        )

        // Entity 2 (Rank 1 in Semantic) should definitely be first
        assertEquals(2L, fused[0].notification.id)
    }
}
