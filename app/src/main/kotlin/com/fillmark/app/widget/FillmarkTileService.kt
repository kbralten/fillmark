package com.fillmark.app.widget

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.sync.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class FillmarkTileService : TileService() {

    private var serviceScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        serviceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        scope.launch {
            refreshTileState()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onClick() {
        super.onClick()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val database = FillmarkDatabase.getInstance(applicationContext)
            val prefsRepo = UserPreferencesRepository(applicationContext)
            val prefs = prefsRepo.preferencesFlow.first()
            val topPreset = database.presetDao().getAllPresets().firstOrNull()

            val volumeMl = topPreset?.volumeMl ?: 250.0
            val label = topPreset?.label ?: "Water"
            val factor = topPreset?.hydrationFactor ?: 1.0
            val drinkType = topPreset?.drinkType ?: "WATER"
            val iconKey = topPreset?.iconKey

            val record = HydrationRecordEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                volumeMl = volumeMl,
                hydrationFactor = factor,
                drinkType = drinkType,
                iconKey = iconKey,
                lastModified = System.currentTimeMillis()
            )
            database.hydrationDao().insertRecord(record)

            if (prefs.healthConnectSyncEnabled) {
                HealthConnectManager.enqueueImmediateSync(applicationContext, replace = true)
            }
            WidgetUpdateHelper.updateAllWidgets(applicationContext)

            val volStr = VolumeConverter.formatVolume(volumeMl, prefs.displayUnit)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Logged +$volStr ($label)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            refreshTileState()
        }
    }

    private suspend fun refreshTileState() {
        runCatching {
            val tile = qsTile ?: return
            val database = FillmarkDatabase.getInstance(applicationContext)
            val prefs = UserPreferencesRepository(applicationContext).preferencesFlow.first()
            val topPreset = database.presetDao().getAllPresets().firstOrNull()

            val today = LocalDate.now()
            val records = database.hydrationDao().getRecordsBetween(
                TimeUtils.startOfDayMillis(today),
                TimeUtils.endOfDayMillis(today)
            )
            val todayTotal = records.sumOf { it.countedVolumeMl(prefs.hydrationFactorsEnabled) }
            val targetEntity = database.dailyTargetDao().getTargetForDate(TimeUtils.currentDateIso())
            val targetMl = targetEntity?.targetMl ?: prefs.defaultDailyTargetMl

            val consumedText = VolumeConverter.formatVolume(todayTotal, prefs.displayUnit)
            val subtitleText = if (targetMl > 0) {
                "$consumedText / ${VolumeConverter.formatVolume(targetMl, prefs.displayUnit)}"
            } else {
                consumedText
            }

            withContext(Dispatchers.Main) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = if (topPreset != null) {
                    "Log ${topPreset.label}"
                } else {
                    "Log Water"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = subtitleText
                }
                tile.updateTile()
            }
        }
    }
}
