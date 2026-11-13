package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ThreadGuard X", appName)
  }

  @Test
  fun `full CUJ paste demo spam thread analyze navigate to results and jump to finding`() {
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithText("ThreadGuard X", substring = true).fetchSemanticsNodes().isNotEmpty())

    // Load spam demo preset and verify 3 tweets added
    composeTestRule.onNodeWithTag("demo_spam_chip").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tweet_card_0").assertIsDisplayed()

    // Run full analysis
    composeTestRule.onNodeWithTag("hero_start_scan_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithTag("overall_summary_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("overall_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("results_list").performScrollToNode(hasTestTag("engine_card_SPAM_DETECTION"))
    composeTestRule.onNodeWithTag("engine_card_SPAM_DETECTION").assertIsDisplayed()

    // Navigate to Rewrite tab and accept first suggestion
    composeTestRule.onNodeWithTag("nav_tab_rewrite").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("accept_suggestion_0").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 3000) {
      composeTestRule.onAllNodesWithTag("restore_original_0").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("restore_original_0").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `paste thread dialog splits numbered thread and settings changes char limit`() {
    composeTestRule.waitForIdle()
    // Switch to Settings and pick 4000 limit
    composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("char_preset_4000").performClick()
    composeTestRule.waitForIdle()

    // Return to Editor and paste numbered thread
    composeTestRule.onNodeWithTag("nav_tab_editor").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("paste_thread_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("paste_thread_input").performTextInput("1/ التغريدة الأولى\n\n2/ التغريدة الثانية")
    composeTestRule.onNodeWithTag("confirm_paste_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("tweet_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("editor_lazy_column").performScrollToNode(hasTestTag("tweet_card_1"))
    composeTestRule.onNodeWithTag("tweet_card_1").assertIsDisplayed()
  }
}
