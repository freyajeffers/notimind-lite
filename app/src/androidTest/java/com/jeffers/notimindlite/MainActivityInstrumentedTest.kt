package com.jeffers.notimindlite

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.ui.MainNavigationGraph
import com.jeffers.notimindlite.ui.theme.NotiMindLiteTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun mainActivity_launchesAndDisplaysNavigationTabs() {
        composeTestRule.setContent {
            NotiMindLiteTheme {
                MainNavigationGraph(dao = database.notificationDao())
            }
        }
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
    }

    @Test
    fun mainActivity_navigatesToHistoryTabOnSelect() {
        composeTestRule.setContent {
            NotiMindLiteTheme {
                MainNavigationGraph(dao = database.notificationDao())
            }
        }
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.onNodeWithText("Search dismissed logs (Time Dismissed)...").assertIsDisplayed()
    }
}
