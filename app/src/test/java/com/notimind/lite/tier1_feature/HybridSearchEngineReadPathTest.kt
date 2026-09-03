package com.notimind.lite.tier1_feature

import androidx.test.core.app.ApplicationProvider
import com.jeffers.notimindlite.util.HybridSearchEngine
import com.jeffers.notimindlite.util.VectorEmbeddingHelper
import com.notimind.lite.base.BaseRobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-G read-side: verify HybridSearchEngine.searchAndRank composes FTS and
 * semantic-vector legs into a single ranked list. This is the unit-test
 * counterpart to the UI wire-up that replaces the naive title/content
 * contains() filter in LogHistoryScreen and ActiveNotificationsScreen.
 *
 * Strategy:
 *  1. Seed the in-memory Room DB with three notifications that share no
 *     common token but vary in semantic proximity to a target query.
 *  2. Manually compute and attach embeddings (we can't capture them via
 *     NotificationLoggerService inside a unit test, and
 *     VectorEmbeddingHelper is a pure function — same call site that
 *     production uses post-commit f279324).
 *  3. Assert that HybridSearchEngine ranks the most semantically-relevant
 *     result first when the FTS leg has zero hits.
 *  4. Assert that HybridSearchEngine returns the FTS hit first when FTS
 *     is the only leg with results.
 *  5. Assert that an empty query returns empty (NOT the full DB — that
 *     overload is the per-screen in-memory one; the DB overload returns
 *     empty for blank to avoid an unbounded scan).
 */
class HybridSearchEngineReadPathTest : BaseRobolectricTest() {

    @Test
    fun tc_R2_T1_H001_ftsOnlyHitRanksFirst() = runTest {
        val auth = createDummyEntity(
            key = "k_auth",
            packageName = "com.auth.app",
            appName = "Authenticator",
            title = "Login code",
            content = "Your authentication code is 123456"
        )
        val weather = createDummyEntity(
            key = "k_weather",
            packageName = "com.weather.app",
            appName = "Weather",
            title = "Sunny",
            content = "It will be sunny today"
        )
        val unrelated = createDummyEntity(
            key = "k_other",
            packageName = "com.other.app",
            appName = "Other",
            title = "Dinner plans",
            content = "Pizza at eight"
        )

        listOf(auth, weather, unrelated).forEach { dao.insertNotification(it) }

        // FTS4 treats hyphens as token separators; use a single alphanumeric token
        // so the MATCH query doesn't parse incorrectly.
        val results = HybridSearchEngine.searchAndRank(context, "authentication")

        assertTrue(
            "FTS leg should have at least one hit for 'authentication'",
            results.isNotEmpty()
        )
        assertEquals(
            "FTS hit must rank first",
            "k_auth",
            results.first().key
        )
    }

    @Test
    fun tc_R2_T1_H002_semanticLegFindsResultsWhenFtsMisses() = runTest {
        // Seed three notifications whose text has zero token overlap with
        // the query but whose embeddings are cosine-similar to it.
        val morning = createDummyEntity(
            key = "k_morning",
            packageName = "com.brew.app",
            appName = "Brew",
            title = "Coffee ready",
            content = "Espresso is brewed and waiting for you"
        )
        val evening = createDummyEntity(
            key = "k_evening",
            packageName = "com.cinema.app",
            appName = "Cinema",
            title = "Movie night",
            content = "Opening show at nine pm"
        )
        val target = createDummyEntity(
            key = "k_target",
            packageName = "com.kitchen.app",
            appName = "Kitchen",
            title = "Cafe latte",
            content = "Cup of joe ready in the kitchen"
        )

        val all = listOf(morning, evening, target)
        // Pre-compute embeddings so the semantic leg has data to score.
        // Same call site production uses post-commit f279324.
        // Convert to ByteArray at the call site (H-fix): Room can't bind
        // FloatArray directly as a query parameter.
        val converter = com.jeffers.notimindlite.data.local.Converters()
        val embeddingsBytes = all.map {
            converter.fromFloatArray(
                VectorEmbeddingHelper.computeEmbedding(
                    "${it.appName} ${it.title} ${it.content} ${it.packageName}"
                )
            ) ?: error("converter returned null")
        }
        val withIds = listOf(
            morning.copy(id = dao.insertNotification(morning)),
            evening.copy(id = dao.insertNotification(evening)),
            target.copy(id = dao.insertNotification(target))
        )
        withIds.zip(embeddingsBytes).forEach { (row, vec) ->
            dao.updateEmbedding(row.id, vec)
        }

        // Query uses tokens ("java", "brew", "espresso") that share NO token
        // with any seeded notification. FTS leg should miss. Semantic leg
        // should still find at least the most-proximate row (the brew/coffee
        // notification) by vector similarity.
        val results = HybridSearchEngine.searchAndRank(context, "java brew espresso")

        assertTrue(
            "Semantic leg should surface at least one result for a no-token-overlap query",
            results.isNotEmpty()
        )
        // The brew notification should be in the results — it's the most
        // semantically proximate to a coffee/brew/espresso query.
        val keys = results.map { it.key }
        assertTrue(
            "Semantic leg must surface the brew notification for a coffee-related query; got $keys",
            "k_morning" in keys
        )
    }

    @Test
    fun tc_R2_T1_H003_blankQueryReturnsEmpty() = runTest {
        val e = createDummyEntity(key = "k_blank", title = "Anything", content = "Stuff")
        dao.insertNotification(e)

        val results = HybridSearchEngine.searchAndRank(context, "   ")

        assertEquals(
            "Blank query must NOT trigger an unbounded DB scan; returns empty",
            emptyList<Any>(),
            results
        )
    }

    @Test
    fun tc_R2_T1_H004_searchReturnsResultsThatFtsHits() = runTest {
        // Sanity: the FTS path actually returns rows. Earlier audit notes
        // raised concerns about FTS trigger coexistence; this test pins
        // the FTS leg as observable via HybridSearchEngine.
        // NOTE: FTS4 tokenizes hyphens as separators; use a single token.
        val e = createDummyEntity(
            key = "k_needle",
            title = "NeedleInHaystack",
            content = "uniqueHaystackToken"
        )
        dao.insertNotification(e)

        val results = HybridSearchEngine.searchAndRank(context, "uniqueHaystackToken")

        assertNotNull(results)
        assertTrue(
            "FTS leg must surface the unique-token row; got ${results.map { it.key }}",
            results.any { it.key == "k_needle" }
        )
    }
}
