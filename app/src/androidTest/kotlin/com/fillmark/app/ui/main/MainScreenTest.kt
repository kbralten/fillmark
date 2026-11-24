package com.fillmark.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fillmark.app.MainNavigation
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.ui.onboarding.OnboardingScreen
import com.fillmark.app.ui.theme.FillmarkTheme
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainNavigation] and [OnboardingScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottomNavigationTabs_existAndSwitch() {
        composeTestRule.setContent {
            FillmarkTheme {
                MainNavigation()
            }
        }

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Analytics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.onNodeWithText("Intake History").assertIsDisplayed()

        composeTestRule.onNodeWithText("Analytics").performClick()
        composeTestRule.onNodeWithText("Drink Analytics").assertIsDisplayed()

        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Display Units").assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_navigatesThroughSteps() {
        composeTestRule.setContent {
            FillmarkTheme {
                OnboardingScreen(
                    initialUnit = DisplayUnit.METRIC_ML,
                    onCompleted = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()
    }
}
