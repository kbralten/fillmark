package com.fillmark.app.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkNavyTitle
import com.fillmark.app.ui.theme.FillmarkSlateMuted

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPresetBar(
    presets: List<PresetEntity>,
    displayUnit: DisplayUnit,
    onPresetClick: (PresetEntity) -> Unit,
    onEditPreset: (PresetEntity) -> Unit,
    onCustomizePresets: () -> Unit,
    onAddPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FillmarkNavyTitle
            )
            IconButton(
                onClick = onCustomizePresets,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Customize presets",
                    tint = FillmarkSlateMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    displayUnit = displayUnit,
                    onClick = { onPresetClick(preset) },
                    onLongClick = { onEditPreset(preset) }
                )
            }

            // Add Preset Card (matching tinted squircle style)
            val newCardBg = getBlendedTileColor(FillmarkCyan)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = newCardBg
                ),
                border = BorderStroke(1.dp, FillmarkCyan.copy(alpha = 0.35f)),
                modifier = Modifier
                    .width(90.dp)
                    .height(118.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = onAddPreset)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add custom preset",
                        tint = FillmarkCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FillmarkCyan
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: PresetEntity,
    displayUnit: DisplayUnit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val presetColor = parseHexColor(preset.colorHex)
    val tileBg = getBlendedTileColor(presetColor)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = tileBg
        ),
        border = BorderStroke(1.dp, presetColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(104.dp)
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val iconRes = PresetIcon.getDrawableRes(preset.iconKey)
            Box(
                modifier = Modifier
                    .size(34.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = preset.label,
                    tint = presetColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, displayUnit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+$volumeStr",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = presetColor
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Log",
                    tint = presetColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

fun getBlendedTileColor(presetColor: Color): Color {
    val r = (0x14 * (1f - 0.28f) + (presetColor.red * 255f) * 0.28f).toInt().coerceIn(0, 255)
    val g = (0x20 * (1f - 0.28f) + (presetColor.green * 255f) * 0.28f).toInt().coerceIn(0, 255)
    val b = (0x2E * (1f - 0.28f) + (presetColor.blue * 255f) * 0.28f).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (_: Exception) {
        FillmarkCyan
    }
}

fun getPresetVector(iconKey: String?): Int {
    return PresetIcon.getDrawableRes(iconKey)
}
