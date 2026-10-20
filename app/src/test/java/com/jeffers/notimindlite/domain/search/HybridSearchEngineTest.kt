package com.jeffers.notimindlite.domain.search

import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.util.VectorEmbeddingHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSearchEngineTest {
    private fun createNotification(
        id: Long = 1,
        title: String,
        content: String,
        appName: String = "TestApp",
        packageName: String = "com.test.app",
        embedding: FloatArray? = null
    ) = NotificationEntity(
        id = id,
        key = "key_$id",
        packageName = packageName,
        appName = appName,
        title = title,
        content = content,
        postTime = System.currentTimeMillis(),
        embedding = embedding
    )

    @Test
    fun tokenizeQuery_splitsOnWhitespaceAndStripsPunctuation() {
        val query = "  Alert: \"Security\" update!  #urgent  "
        val tokens = HybridSearchEngine.tokenizeQuery(query)

        assertEquals(listOf("Alert", "Security", "update", "urgent"), tokens)
    }

    @Test
    fun tokenizeQuery_deduplicatesCaseInsensitiveTokens() {
        val query = "GitHub github GITHUB"
        val tokens = HybridSearchEngine.tokenizeQuery(query)

        assertEquals(1, tokens.size)
        assertEquals("GitHub", tokens[0])
    }

    @Test
    fun tokenizeQuery_emptyOrPunctuationOnly_returnsEmptyList() {
        assertTrue(HybridSearchEngine.tokenizeQuery("").isEmpty())
        assertTrue(HybridSearchEngine.tokenizeQuery("   ").isEmpty())
        assertTrue(HybridSearchEngine.tokenizeQuery("!@#$%^&*()").isEmpty())
    }

    @Test
    fun searchAndRankBlocking_emptyQuery_returnsAllNotificationsUnchanged() {
        val list = listOf(
            createNotification(1, "Title 1", "Content 1"),
            createNotification(2, "Title 2", "Content 2")
        )
        val result = HybridSearchEngine.searchAndRankBlocking(list, "")
        assertEquals(list, result)
    }

    @Test
    fun searchAndRankBlocking_filtersNonMatchingEntities() {
        val target = createNotification(1, "Bank OTP", "Your login code is 123456")
        val other = createNotification(2, "Shopping", "Discounts available today")
        val result = HybridSearchEngine.searchAndRankBlocking(listOf(target, other), "OTP code")

        assertEquals(1, result.size)
        assertEquals(target.id, result[0].id)
    }

    @Test
    fun searchAndRankBlocking_ranksExactTitleMatchHigher() {
        val partial = createNotification(1, "System Notice", "Contains Security in the description body")
        val exact = createNotification(2, "Security", "Short message")
        val result = HybridSearchEngine.searchAndRankBlocking(listOf(partial, exact), "Security")

        assertEquals(2, result.size)
        assertEquals("Security", result[0].title)
    }

    @Test
    fun searchAndRankBlocking_caseInsensitiveMatching() {
        val notif = createNotification(1, "URGENT PAYMENT", "Payment processed")
        val result = HybridSearchEngine.searchAndRankBlocking(listOf(notif), "urgent payment")

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun searchAndRankBlocking_matchesAppNameAndPackageNameTokens() {
        val notifApp = createNotification(1, "Meeting", "Discuss project", appName = "SlackWork")
        val notifPkg = createNotification(2, "Reminder", "Doctor visit", packageName = "com.calendar.org")
        val other = createNotification(
            id = 3,
            title = "Delivery",
            content = "Food arriving",
            appName = "UberEats",
            packageName = "com.ubercab.eats"
        )

        val appMatches = HybridSearchEngine.searchAndRankBlocking(listOf(notifApp, notifPkg, other), "SlackWork")
        assertEquals(1, appMatches.size)
        assertEquals(1L, appMatches[0].id)

        val pkgMatches = HybridSearchEngine.searchAndRankBlocking(listOf(notifApp, notifPkg, other), "calendar")
        assertEquals(1, pkgMatches.size)
        assertEquals(2L, pkgMatches[0].id)
    }

    @Test
    fun searchAndRank_suspendMatchesBlockingResults() = runBlocking {
        val n1 = createNotification(1, "Weekly Summary", "Here is your report", appName = "Email")
        val n2 = createNotification(2, "Daily Digest", "Your morning digest", appName = "News")
        val list = listOf(n1, n2)

        val blockingResult = HybridSearchEngine.searchAndRankBlocking(list, "digest")
        val suspendResult = HybridSearchEngine.searchAndRank(list, "digest")

        assertEquals(blockingResult.map { it.id }, suspendResult.map { it.id })
    }

    @Test
    fun searchAndRank_utilFacadeDelegatesCorrectly() = runBlocking {
        val n1 = createNotification(1, "Security Alert", "New login detected")
        val list = listOf(n1)

        val blockingFacade = com.jeffers.notimindlite.util.HybridSearchEngine
            .searchAndRankBlocking(list, "Security")
        val suspendFacade = com.jeffers.notimindlite.util.HybridSearchEngine
            .searchAndRank(list, "Security")

        assertEquals(1, blockingFacade.size)
        assertEquals(1, suspendFacade.size)
        assertEquals(1L, blockingFacade[0].id)
        assertEquals(1L, suspendFacade[0].id)
    }

    @Test
    fun searchAndRank_withSemanticVectorEmbeddings_ranksSemanticMatches() {
        val cryptoText = "Bitcoin ethereum cryptocurrency wallet transfer"
        val weatherText = "Sunny forecast temperature 25 degrees"

        val nCrypto = createNotification(
            id = 10,
            title = "Finance",
            content = "Update available",
            embedding = VectorEmbeddingHelper.computeEmbedding(cryptoText)
        )
        val nWeather = createNotification(
            id = 20,
            title = "Weather",
            content = "Daily bulletin",
            embedding = VectorEmbeddingHelper.computeEmbedding(weatherText)
        )

        // Querying "cryptocurrency" without matching exact title or content words of Finance
        val results = HybridSearchEngine.searchAndRankBlocking(listOf(nWeather, nCrypto), "cryptocurrency")
        assertEquals(2, results.size)
        assertEquals(10L, results[0].id)
        assertEquals(20L, results[1].id)
    }
}
