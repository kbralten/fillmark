package com.fillmark.app.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fillmark.app.core.DisplayUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitToggle(
    selectedUnit: DisplayUnit,
    onUnitSelected: (DisplayUnit) -> Unit,
    modifier: Modifier = Modifier.width(128.dp)
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = selectedUnit == DisplayUnit.METRIC_ML,
            onClick = { onUnitSelected(DisplayUnit.METRIC_ML) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {},
            label = { Text("ml") }
        )
        SegmentedButton(
            selected = selectedUnit == DisplayUnit.IMPERIAL_FL_OZ,
            onClick = { onUnitSelected(DisplayUnit.IMPERIAL_FL_OZ) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {},
            label = { Text("oz") }
        )
    }
}
