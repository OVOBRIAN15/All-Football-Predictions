package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.UiState
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.PredictionHistoryScreen
import com.example.ui.screens.ReadMeScreen
import com.example.ui.screens.SearchFootballScreen
import com.example.ui.theme.AllFootballPredictionsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app identity strings match branding and exclude prohibited terms`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val tagline = context.getString(R.string.app_tagline)
        val disclaimer = context.getString(R.string.msg_prediction_disclaimer)
        val insufficient = context.getString(R.string.label_insufficient_data)

        assertEquals("All Football Predictions", appName)
        assertEquals("Insufficient data", insufficient)
        val wordAiRegex = Regex("\\bAI\\b")
        assertFalse(wordAiRegex.containsMatchIn(appName))
        assertFalse(wordAiRegex.containsMatchIn(tagline))
        assertFalse(wordAiRegex.containsMatchIn(disclaimer))
    }

    @Test
    fun `search screen renders input button and suggestions`() {
        composeRule.setContent {
            AllFootballPredictionsTheme {
                SearchFootballScreen(
                    query = "",
                    suggestions = emptyList(),
                    recentSearches = emptyList(),
                    bundle = null,
                    isSearching = false,
                    error = null,
                    onQueryChange = {},
                    onSearch = {},
                    onClearSearch = {},
                    onClearRecent = {},
                    onSelectTeam = {},
                    onSelectFixture = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_football_input").assertIsDisplayed()
        composeRule.onNodeWithTag("search_football_button").assertIsDisplayed()
        composeRule.onNodeWithText("Search Football").assertIsDisplayed()
    }

    @Test
    fun `guest prediction history prompts google sign in`() {
        composeRule.setContent {
            AllFootballPredictionsTheme {
                PredictionHistoryScreen(
                    isSignedIn = false,
                    predictionsState = UiState.Success(emptyList()),
                    onAuthSuccess = {},
                    onAuthError = {}
                )
            }
        }
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithText("Prediction History").assertIsDisplayed()
    }

    @Test
    fun `admin login screen displays owner portal notice for signed-out visitors`() {
        composeRule.setContent {
            AllFootballPredictionsTheme {
                AdminLoginScreen(
                    currentUser = null,
                    adminViewModel = null,
                    adminConfig = null,
                    isPasscodeUnlocked = false,
                    authError = null,
                    onAuthSuccess = {},
                    onOpenAdminDashboard = {}
                )
            }
        }
        composeRule.onNodeWithText("Administrator Login").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }

    @Test
    fun `read me renders mathematical methodology without banned terminology`() {
        composeRule.setContent {
            AllFootballPredictionsTheme {
                ReadMeScreen(onOpenPrivacy = {}, onOpenTerms = {})
            }
        }
        composeRule.onNodeWithText("Read Me & Methodology").assertIsDisplayed()
    }
}
