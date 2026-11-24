package com.fillmark.app.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fillmark.app.R

enum class PresetIcon(
    val id: String,
    val displayName: String,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int
) {
    GLASS("GLASS", "Glass", R.drawable.ic_preset_glass, R.string.preset_icon_glass),
    MUG("MUG", "Mug", R.drawable.ic_preset_mug, R.string.preset_icon_mug),
    WATER_BOTTLE("WATER_BOTTLE", "Water Bottle", R.drawable.ic_preset_bottle, R.string.preset_icon_bottle),
    CAN("CAN", "Can", R.drawable.ic_preset_can, R.string.preset_icon_can),
    TUMBLER("TUMBLER", "Tumbler", R.drawable.ic_preset_tumbler, R.string.preset_icon_tumbler);

    companion object {
        fun fromId(id: String?): PresetIcon {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GLASS
        }

        fun getDrawableRes(iconKey: String?): Int {
            return fromId(iconKey).drawableRes
        }

        fun defaultForDrinkType(drinkType: DrinkType): PresetIcon {
            return when (drinkType) {
                DrinkType.COFFEE -> MUG
                DrinkType.TEA -> TUMBLER
                DrinkType.SODA -> CAN
                else -> GLASS
            }
        }
    }
}
