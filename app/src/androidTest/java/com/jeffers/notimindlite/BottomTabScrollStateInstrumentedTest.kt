package com.jeffers.notimindlite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.notimindlite.ui.Screen
import com.jeffers.notimindlite.ui.bottomTabNavigationOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomTabScrollStateInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollPositionIsRestoredAfterSwitchingTabs() {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = Screen.Active.route) {
                composable(Screen.Active.route) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                navController.navigate(
                                    Screen.History.route,
                                    bottomTabNavigationOptions()
                                )
                            }
                        ) {
                            Text("Open History")
                        }
                    }
                }
                composable(Screen.History.route) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                navController.navigate(
                                    Screen.Settings.route,
                                    bottomTabNavigationOptions()
                                )
                            }
                        ) {
                            Text("Open Settings")
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            state = rememberLazyListState()
                        ) {
                            items(60) { index ->
                                Text(
                                    text = "History item $index",
                                    modifier = Modifier.testTag("history-item-$index")
                                )
                            }
                        }
                    }
                }
                composable(Screen.Settings.route) {
                    Button(
                        onClick = {
                            navController.navigate(
                                Screen.History.route,
                                bottomTabNavigationOptions()
                            )
                        }
                    ) {
                        Text("Return to History")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Open History").performClick()
        composeRule.onNodeWithTag("history-item-40").performScrollTo()
        composeRule.onNodeWithText("Open Settings").performClick()
        composeRule.onNodeWithText("Return to History").performClick()
        composeRule.onNodeWithTag("history-item-40").assertIsDisplayed()
    }
}
