package com.fillmark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.ui.theme.FillmarkTheme
import com.fillmark.app.widget.MidnightAlarmScheduler
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      FillmarkTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onResume() {
    super.onResume()
    MidnightAlarmScheduler.scheduleMidnightReset(this)
    lifecycleScope.launch {
      WidgetUpdateHelper.updateAllWidgets(this@MainActivity)
      val prefs = UserPreferencesRepository(this@MainActivity).preferencesFlow.first()
      if (prefs.healthConnectSyncEnabled) {
        HealthConnectManager.schedulePeriodicSync(this@MainActivity)
        HealthConnectManager.enqueueImmediateSync(this@MainActivity)
      }
    }
  }
}
