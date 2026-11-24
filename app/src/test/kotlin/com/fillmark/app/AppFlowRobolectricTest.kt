package com.fillmark.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.ui.dashboard.DashboardScreen
import com.fillmark.app.ui.dashboard.DashboardViewModel
import com.fillmark.app.ui.history.HistoryScreen
import com.fillmark.app.ui.history.HistoryViewModel
import com.fillmark.app.ui.onboarding.OnboardingScreen
import com.fillmark.app.ui.settings.SettingsScreen
import com.fillmark.app.ui.settings.SettingsViewModel
import com.fillmark.app.ui.theme.FillmarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var db: FillmarkDatabase
    private lateinit var prefsRepo: UserPreferencesRepository

    private fun waitUntilWithLooper(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            ShadowLooper.idleMainLooper()
            condition()
        }
    }

    @Before
    fun setup() {
        FillmarkDatabase.resetInstanceForTesting()
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("fillmark_database")
        db = FillmarkDatabase.getInstance(app)
        prefsRepo = UserPreferencesRepository(app)
        runBlocking {
            db.hydrationDao().deleteAllRecords()
            db.presetDao().deleteAllPresets()
            db.dailyTargetDao().deleteAllTargets()
            db.pendingHealthConnectDeletionDao().deleteAll()
        }
    }

    @After
    fun tearDown() {
        FillmarkDatabase.resetInstanceForTesting()
        app.deleteDatabase("fillmark_database")
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun onboarding_completesAndPersistsPreferences() {
        var completed = false

        composeTestRule.setContent {
            FillmarkTheme {
                OnboardingScreen(
                    initialUnit = DisplayUnit.METRIC_ML,
                    onCompleted = { completed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Imperial (fl oz)").performClick()
        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithText("100 fl oz").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Next").performClick()

        composeTestRule.onNodeWithText("Get Started").performScrollTo().assertIsDisplayed().performClick()

        waitUntilWithLooper { completed }

        val prefs = runBlocking { prefsRepo.preferencesFlow.first() }
        assertTrue(prefs.onboardingCompleted)
        assertEquals(DisplayUnit.IMPERIAL_FL_OZ, prefs.displayUnit)
        assertEquals(2957.35, prefs.defaultDailyTargetMl, 1.0)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun dashboard_quickLogAndUndo_updatesRoomAndReverts() {
        runBlocking {
            db.presetDao().insertPresets(
                listOf(
                    PresetEntity(
                        id = "preset-glass",
                        label = "Glass",
                        volumeMl = 250.0,
                        iconKey = "glass",
                        hydrationFactor = 1.0,
                        displayOrder = 0,
                        drinkType = "WATER",
                        colorHex = "#2196F3"
                    )
                )
            )
        }

        val viewModel = DashboardViewModel(app)
        composeTestRule.setContent {
            FillmarkTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }

        waitUntilWithLooper {
            composeTestRule.onAllNodesWithText("Glass").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Glass").performClick()

        waitUntilWithLooper {
            runBlocking { db.hydrationDao().getAllRecords().size == 1 }
        }
        val logged = runBlocking { db.hydrationDao().getAllRecords().single() }
        assertEquals(250.0, logged.volumeMl, 0.01)
        assertEquals("Glass", logged.label)

        waitUntilWithLooper {
            composeTestRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Undo").performClick()

        waitUntilWithLooper {
            runBlocking { db.hydrationDao().getAllRecords().isEmpty() }
        }
    }

    @Test
    fun history_editAndDeleteRecord_persistsToRoom() {
        runBlocking {
            prefsRepo.updateDisplayUnit(DisplayUnit.METRIC_ML)
            db.hydrationDao().insertRecord(
                HydrationRecordEntity(
                    id = "rec-edit-test",
                    timestamp = System.currentTimeMillis() - 60_000L,
                    volumeMl = 250.0,
                    hydrationFactor = 1.0,
                    drinkType = "WATER",
                    iconKey = "glass",
                    label = "Morning Water"
                )
            )
        }

        val viewModel = HistoryViewModel(app)
        composeTestRule.setContent {
            FillmarkTheme {
                HistoryScreen(viewModel = viewModel)
            }
        }

        waitUntilWithLooper {
            composeTestRule.onAllNodesWithText("Morning Water").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Morning Water").performClick()

        composeTestRule.onNodeWithText("Edit Drink Entry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Volume (ml)").performTextReplacement("500")
        composeTestRule.onNodeWithText("Drink Name (optional)").performTextReplacement("Big Bottle")
        composeTestRule.onNodeWithText("Save").performClick()

        waitUntilWithLooper {
            runBlocking {
                val updated = db.hydrationDao().getRecordById("rec-edit-test")
                updated != null && abs(updated.volumeMl - 500.0) < 0.01 && updated.label == "Big Bottle"
            }
        }

        waitUntilWithLooper {
            composeTestRule.onAllNodesWithText("Big Bottle").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Big Bottle").performClick()
        composeTestRule.onNodeWithContentDescription("Delete entry").performClick()

        waitUntilWithLooper {
            runBlocking { db.hydrationDao().getAllRecords().isEmpty() }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun settings_defaultTargetUpdate_persistsAndPreservesHistory() {
        runBlocking {
            prefsRepo.updateDisplayUnit(DisplayUnit.METRIC_ML)
            prefsRepo.updateDefaultDailyTargetMl(2500.0)
            db.dailyTargetDao().upsertTarget(
                DailyTargetEntity(
                    dateString = "2026-11-22",
                    targetMl = 2000.0
                )
            )
        }

        val viewModel = SettingsViewModel(app)
        composeTestRule.setContent {
            FillmarkTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        waitUntilWithLooper {
            composeTestRule.onAllNodesWithText("Daily Target").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Daily Target").performScrollTo().performTextReplacement("3000")
        composeTestRule.onNodeWithText("Apply").performClick()

        waitUntilWithLooper {
            runBlocking {
                abs(prefsRepo.preferencesFlow.first().defaultDailyTargetMl - 3000.0) < 0.01
            }
        }

        val pastTarget = runBlocking { db.dailyTargetDao().getTargetForDate("2026-11-22") }
        assertEquals(2000.0, pastTarget?.targetMl ?: 0.0, 0.01)
    }
}
