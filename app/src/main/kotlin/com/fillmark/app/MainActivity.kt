package com.fillmark.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.ui.onboarding.OnboardingScreen
import com.fillmark.app.ui.theme.FillmarkTheme
import com.fillmark.app.widget.MidnightAlarmScheduler
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    internal companion object {
        fun initialCompositionPrefs(loadedPrefs: UserPreferences?): UserPreferences {
            return loadedPrefs ?: UserPreferences(onboardingCompleted = false)
        }

        suspend fun resolveInitialPreferences(
            loadedPrefs: UserPreferences,
            hasAnyRecords: suspend () -> Boolean,
            markOnboardingCompleted: suspend () -> Unit
        ): UserPreferences {
            return if (!loadedPrefs.onboardingCompleted && hasAnyRecords()) {
                markOnboardingCompleted()
                loadedPrefs.copy(onboardingCompleted = true)
            } else {
                loadedPrefs
            }
        }
    }

    private var initialPrefs by mutableStateOf<UserPreferences?>(null)
    private var pendingShortcutPresetId by mutableStateOf<String?>(null)
    private var pendingOpenManualLog by mutableStateOf(false)
    private var foregroundInboundSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { initialPrefs == null }
        handleIntentExtras(intent)

        val prefsRepo = UserPreferencesRepository(this)
        val hydrationRepo = HydrationRepository(this)

        lifecycleScope.launch {
            val loaded = prefsRepo.preferencesFlow.first()
            // Skip onboarding automatically for upgrading users who already have records
            initialPrefs = resolveInitialPreferences(
                loadedPrefs = loaded,
                hasAnyRecords = { hydrationRepo.hasAnyRecords() },
                markOnboardingCompleted = { prefsRepo.setOnboardingCompleted(true) }
            )
        }

        enableEdgeToEdge()
        setContent {
            val loaded = initialPrefs ?: return@setContent
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = initialCompositionPrefs(loaded)
            )
            val isDark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }

            FillmarkTheme(
                darkTheme = isDark,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!prefs.onboardingCompleted) {
                        OnboardingScreen(
                            initialUnit = prefs.displayUnit,
                            onCompleted = {}
                        )
                    } else {
                        MainNavigation(
                            pendingShortcutPresetId = pendingShortcutPresetId,
                            pendingOpenManualLog = pendingOpenManualLog,
                            onShortcutHandled = {
                                pendingShortcutPresetId = null
                                pendingOpenManualLog = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        val presetId = intent?.getStringExtra(ShortcutHelper.EXTRA_QUICK_LOG_PRESET_ID)
        val openManual = intent?.getBooleanExtra(ShortcutHelper.EXTRA_OPEN_MANUAL_LOG, false) == true
        if (presetId != null) {
            pendingShortcutPresetId = presetId
            ShortcutManagerCompat.reportShortcutUsed(this, "preset_$presetId")
            intent.removeExtra(ShortcutHelper.EXTRA_QUICK_LOG_PRESET_ID)
        }
        if (openManual) {
            pendingOpenManualLog = true
            ShortcutManagerCompat.reportShortcutUsed(this, "manual_log")
            intent.removeExtra(ShortcutHelper.EXTRA_OPEN_MANUAL_LOG)
        }
    }

    override fun onResume() {
        super.onResume()
        MidnightAlarmScheduler.scheduleMidnightReset(this)
        lifecycleScope.launch {
            WidgetUpdateHelper.updateAllWidgets(this@MainActivity)
            val prefsRepo = UserPreferencesRepository(this@MainActivity)
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.healthConnectSyncEnabled) {
                HealthConnectManager.schedulePeriodicSync(this@MainActivity)
                HealthConnectManager.enqueueImmediateSync(this@MainActivity)
                if (prefs.healthConnectBidirectional && foregroundInboundSyncJob?.isActive != true) {
                    foregroundInboundSyncJob = launch {
                        val hcManager = HealthConnectManager(this@MainActivity)
                        if (hcManager.isAvailable() && hcManager.hasReadPermission()) {
                            val db = FillmarkDatabase.getInstance(this@MainActivity)
                            val result = withContext(Dispatchers.IO) {
                                hcManager.syncInbound(db)
                            }
                            if (result.succeeded) {
                                prefsRepo.setLastHealthConnectSyncMillis(System.currentTimeMillis())
                            }
                            if (result.importedCount > 0) {
                                WidgetUpdateHelper.updateAllWidgets(this@MainActivity)
                            }
                        }
                    }
                }
            }
        }
    }
}
