package com.notimind.lite.tier2_boundary

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.ReciprocalRankFusion
import com.jeffers.notimindlite.util.SemanticSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RrfRankingTest {

    private fun createEntity(id: Long, text: String = "Test"): NotificationEntity {
        return NotificationEntity(
            id = id,
            title = "Title $id",
            content = text,
            timestamp = System.currentTimeMillis(),
            category = "General",
            appPackage = "com.test.app"
        )
    }

    @Test
    fun `merge - empty lists return empty result`() {
        val results = ReciprocalRankFusion.merge(emptyList(), emptyList())
        assertEquals(0, results.size)
    }

    @Test
    fun `merge - single source only results in identity ranking`() {
        val e1 = createEntity(1)
        val e2 = createEntity(2)
        
        val ftsResults = listOf(e1, e2)
        val results = ReciprocalRankFusion.merge(ftsResults, emptyList())
        
        assertEquals(2, results.size)
        assertEquals(1L, results[0].notification.id)
        assertEquals(2L, results[1].notification.id)
    }

    @Test
    fun `merge - consensus strongly promotes item`() {
        val e1 = createEntity(1)
        val e2 = createEntity(2)
        val e3 = createEntity(3)

        // e1 is #1 in both
        val fts = listOf(e1, e2, e3)
        val semantic = listOf(
            SemanticSearchResult(e1, 0.9f),
            SemanticSearchResult(e3, 0.8f),
            SemanticSearchResult(e2, 0.7f)
        )

        val results = ReciprocalRankFusion.merge(fts, semantic)

        assertEquals(1L, results[0].notification.id)
        // e3 is #3 FTS, #2 Semantic. e2 is #2 FTS, #3 Semantic.
        // RRF for e3: 1/(60+3) + 1/(60+2) = 1/63 + 1/62
        // RRF for e2: 1/(60+2) + 1/(60+3) = 1/62 + 1/63
        // They should be equal.
        assertEquals(results[1].rrfScore, results[2].rrfScore, 0.0001)
    }

    @Test
    fun `merge - high semantic rank can override low FTS rank`() {
        val e1 = createEntity(1)
        val e2 = createEntity(2)

        // e1 is #10 FTS, but #1 Semantic
        val fts = (2..10).map { createEntity(it.toLong()) } + e1
        val semantic = listOf(SemanticSearchResult(e1, 0.99f))

        val results = ReciprocalRankFusion.merge(fts, semantic)

        // e1 should be significantly promoted
        // e1 score: 1/(60+10) + 1/(60+1) = 1/70 + 1/61 ~= 0.014 + 0.016 = 0.030
        // e2 score: 1/(60+1) + 0 = 1/61 ~= 0.016
        assertEquals(1L, results[0].notification.id)
    }

    @Test
    fun `merge - custom weights shift priority`() {
        val e1 = createEntity(1)
        val e2 = createEntity(2)

        // e1 is #1 FTS, #2 Semantic
        // e2 is #2 FTS, #1 Semantic
        val fts = listOf(e1, e2)
        val semantic = listOf(
            SemanticSearchResult(e2, 0.9f),
            SemanticSearchResult(e1, 0.8f)
        )

        // With equal weights, they are tied.
        // With FTS weight 2.0, e1 should win.
        val results = ReciprocalRankFusion.merge(fts, semantic, ftsWeight = 2.0, semanticWeight = 1.0)
        assertEquals(1L, results[0].notification.id)

        // With Semantic weight 2.0, e2 should win.
        val results2 = ReciprocalRankFusion.merge(fts, semantic, ftsWeight = 1.0, semanticWeight = 2.0)
        assertEquals(2L, results2[0].notification.id)
    }
}
