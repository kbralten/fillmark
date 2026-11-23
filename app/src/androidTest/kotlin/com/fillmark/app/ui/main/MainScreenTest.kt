package com.fillmark.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fillmark.app.MainNavigation
import com.fillmark.app.ui.theme.FillmarkTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainNavigation]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            FillmarkTheme {
                MainNavigation()
            }
        }
    }

    @Test
    fun bottomNavigationTabs_existAndSwitch() {
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Analytics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Display Units").assertIsDisplayed()
    }
}
