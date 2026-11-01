package com.jeffers.notimindlite.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SwipeDismissibleCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeLeftInvokesDismissAction() {
        val dismissed = AtomicBoolean(false)
        composeRule.setContent {
            MaterialTheme {
                SwipeDismissibleCard(onDismiss = {
                    dismissed.set(true)
                    true
                }) {
                    Card(modifier = Modifier.fillMaxWidth().testTag("swipe-card")) {
                        Text("Swipe to dismiss")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("swipe-card").performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertTrue(dismissed.get()) }
    }
}
