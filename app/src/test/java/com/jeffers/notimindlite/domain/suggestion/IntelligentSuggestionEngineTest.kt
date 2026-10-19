package com.jeffers.notimindlite.domain.suggestion

import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class IntelligentSuggestionEngineTest {
    @Before
    fun setUp() {
        IntelligentSuggestionEngine.clearHistoryForTesting()
    }

    @After
    fun tearDown() {
        IntelligentSuggestionEngine.clearHistoryForTesting()
    }

    @Test
    fun predictSuggestions_withEmptyDismissedNotifications_returnsEmptyList() = runBlocking {
        val result = IntelligentSuggestionEngine.predictSuggestions(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun predictSuggestions_withNoSearchHistory_returnsEmptyList() = runBlocking {
        val notifications = listOf(
            createNotification(id = 1, title = "Order Shipped", content = "Your package is on its way")
        )
        val result = IntelligentSuggestionEngine.predictSuggestions(notifications)
        assertTrue(result.isEmpty())
    }

    @Test
    fun predictSuggestions_matchesTemporalWindowAndRanksByScore() = runBlocking {
        val now = System.currentTimeMillis()
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting(
            query = "package",
            ids = listOf(1L),
            timestamp = now
        )

        val notif1 = createNotification(id = 1, title = "Amazon", content = "Your package is arriving today")
        val notif2 = createNotification(id = 2, title = "News", content = "Weather update")

        val suggestions = IntelligentSuggestionEngine.predictSuggestions(listOf(notif1, notif2))
        assertEquals(1, suggestions.size)
        assertEquals(1L, suggestions[0].id)
    }

    @Test
    fun predictSuggestions_ranksByFrequencyAndPositionWeight() = runBlocking {
        val now = System.currentTimeMillis()
        // Record "invoice" twice and "lunch" once
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting("invoice", listOf(1L), now)
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting("invoice", listOf(1L), now)
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting("lunch", listOf(2L), now)

        val invoiceNotif = createNotification(id = 1, title = "Finance", content = "New invoice generated")
        val lunchNotif = createNotification(id = 2, title = "Food", content = "Lunch delivery order ready")

        val suggestions = IntelligentSuggestionEngine.predictSuggestions(listOf(lunchNotif, invoiceNotif))
        assertEquals(2, suggestions.size)
        assertEquals(1L, suggestions[0].id)
        assertEquals(2L, suggestions[1].id)
    }

    @Test
    fun predictSuggestions_fallsBackToGlobalPatternsWhenTemporalWindowMisses() = runBlocking {
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 8)
        }
        // Timestamp is 8 hours away from current hour (outside the 2-hour temporal window)
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting(
            query = "security",
            ids = listOf(10L),
            timestamp = cal.timeInMillis
        )

        val notif = createNotification(id = 10, title = "Auth", content = "Security verification code: 482910")
        val suggestions = IntelligentSuggestionEngine.predictSuggestions(listOf(notif))

        assertEquals(1, suggestions.size)
        assertEquals(10L, suggestions[0].id)
    }

    @Test
    fun predictSuggestions_capsAtMaximumSuggestions() = runBlocking {
        val now = System.currentTimeMillis()
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting("update", listOf(1L), now)

        val notifications = (1..15).map { index ->
            createNotification(
                id = index.toLong(),
                title = "App $index",
                content = "System update version $index available"
            )
        }

        val suggestions = IntelligentSuggestionEngine.predictSuggestions(notifications)
        assertEquals(10, suggestions.size)
    }

    @Test
    fun predictSuggestions_matchesAppNameCaseInsensitively() = runBlocking {
        val now = System.currentTimeMillis()
        IntelligentSuggestionEngine.recordSearchWithTimestampForTesting("chase", listOf(100L), now)

        val notif = createNotification(
            id = 100,
            appName = "Chase Bank",
            title = "Account Statement",
            content = "Monthly statement is ready"
        )

        val suggestions = IntelligentSuggestionEngine.predictSuggestions(listOf(notif))
        assertEquals(1, suggestions.size)
        assertEquals(100L, suggestions[0].id)
    }

    @Test
    fun facade_utilEngineDelegatesCorrectly() = runBlocking {
        com.jeffers.notimindlite.util.IntelligentSuggestionEngine
            .clearHistoryForTesting()
        com.jeffers.notimindlite.util.IntelligentSuggestionEngine
            .recordSearch("flight", listOf(50L))

        val notif = createNotification(id = 50, title = "Airline", content = "Flight confirmation")
        val suggestions = com.jeffers.notimindlite.util.IntelligentSuggestionEngine
            .predictSuggestions(listOf(notif))

        assertEquals(1, suggestions.size)
        assertEquals(50L, suggestions[0].id)
    }

    private fun createNotification(
        id: Long,
        appName: String = "TestApp",
        title: String = "Test Title",
        content: String = "Test Content"
    ): NotificationEntity = NotificationEntity(
        id = id,
        key = "key_$id",
        packageName = "com.test.app",
        appName = appName,
        title = title,
        content = content,
        isDismissed = true
    )
}
