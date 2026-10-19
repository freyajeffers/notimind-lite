package com.jeffers.notimindlite.domain.search

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSearchEngineTest {
    private fun createNotification(
        id: Long = 1,
        title: String,
        content: String,
        appName: String = "TestApp",
        packageName: String = "com.test.app"
    ) = NotificationEntity(
        id = id,
        key = "key_$id",
        packageName = packageName,
        appName = appName,
        title = title,
        content = content,
        postTime = System.currentTimeMillis()
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
}
