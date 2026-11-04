package com.fillmark.app.core

import com.fillmark.app.R

enum class PresetIcon(val id: String, val displayName: String, val drawableRes: Int) {
    GLASS("GLASS", "Glass", R.drawable.ic_preset_glass),
    MUG("MUG", "Mug", R.drawable.ic_preset_mug),
    WATER_BOTTLE("WATER_BOTTLE", "Water Bottle", R.drawable.ic_preset_bottle),
    CAN("CAN", "Can", R.drawable.ic_preset_can),
    TUMBLER("TUMBLER", "Tumbler", R.drawable.ic_preset_tumbler);

    companion object {
        fun fromId(id: String?): PresetIcon {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GLASS
        }

        fun getDrawableRes(iconKey: String?): Int {
            return fromId(iconKey).drawableRes
        }
    }
}
