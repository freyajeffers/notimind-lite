package com.jeffers.notimindlite.ui.screens

import com.jeffers.notimindlite.data.local.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LogHistorySearchHelpersTest {
    @Test
    fun shortQueriesAndEmptyListsReturnNoSuggestions() {
        val notifications = listOf(notification("Signal update", "Signal"))

        assertEquals(emptyList<String>(), logHistorySearchSuggestions("", notifications))
        assertEquals(emptyList<String>(), logHistorySearchSuggestions("s", notifications))
        assertEquals(emptyList<String>(), logHistorySearchSuggestions("signal", emptyList()))
    }

    @Test
    fun suggestionsMatchAppNamesAndTitlesWithoutExactDuplicates() {
        val notifications = listOf(
            notification(title = "Signal update", appName = "Signal", key = "one"),
            notification(title = "Signal archive", appName = "Signal", key = "two")
        )

        assertEquals(
            listOf("Signal", "Signal update", "Signal archive"),
            logHistorySearchSuggestions("sIg", notifications)
        )
    }

    @Test
    fun suggestionsAreLimitedToFourInStableSourceOrder() {
        val notifications = (1..5).map { index ->
            notification(title = "meeting $index", appName = "Calendar", key = "meeting-$index")
        }

        assertEquals(
            listOf("meeting 1", "meeting 2", "meeting 3", "meeting 4"),
            logHistorySearchSuggestions("MEET", notifications)
        )
    }

    @Test
    fun unmatchedQueriesReturnEmptyList() {
        assertEquals(
            emptyList<String>(),
            logHistorySearchSuggestions("missing", listOf(notification("Update", "Signal")))
        )
    }

    private fun notification(title: String, appName: String, key: String = title) = NotificationEntity(
        key = key,
        packageName = "com.example.calendar",
        appName = appName,
        title = title,
        content = "Body"
    )
}
