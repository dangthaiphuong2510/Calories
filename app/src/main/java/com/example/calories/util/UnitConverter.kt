package com.example.calories.util

import android.content.Context
import com.example.calories.R
import com.example.calories.data.preferences.UnitSystem
import kotlin.math.roundToInt

/**
 * Shared metric ↔ imperial conversions. Storage stays metric (kg, g, cm);
 * convert only at the UI boundary based on [UnitSystem].
 */
object UnitConverter {
    const val KG_TO_LB = 2.2046226218
    const val G_TO_OZ = 0.03527396195
    private const val CM_PER_INCH = 2.54

    fun kgToLb(kg: Double): Double = kg * KG_TO_LB
    fun lbToKg(lb: Double): Double = lb / KG_TO_LB

    fun gramsToOz(grams: Double): Double = grams * G_TO_OZ
    fun ozToGrams(oz: Double): Double = oz / G_TO_OZ

    fun weightToDisplay(weightKg: Double, unit: UnitSystem): Double = when (unit) {
        UnitSystem.METRIC -> weightKg
        UnitSystem.IMPERIAL -> kgToLb(weightKg)
    }

    fun weightFromDisplay(display: Double, unit: UnitSystem): Double = when (unit) {
        UnitSystem.METRIC -> display
        UnitSystem.IMPERIAL -> lbToKg(display)
    }

    fun portionToDisplay(grams: Double, unit: UnitSystem): Double = when (unit) {
        UnitSystem.METRIC -> grams
        UnitSystem.IMPERIAL -> {
            val oz = gramsToOz(grams)
            (oz * 10.0).roundToInt() / 10.0
        }
    }

    fun portionFromDisplay(display: Double, unit: UnitSystem): Double = when (unit) {
        UnitSystem.METRIC -> display
        UnitSystem.IMPERIAL -> ozToGrams(display)
    }

    fun formatWeight(context: Context, weightKg: Double?, unit: UnitSystem): String {
        if (weightKg == null) {
            return context.getString(
                when (unit) {
                    UnitSystem.METRIC -> R.string.weight_placeholder_kg
                    UnitSystem.IMPERIAL -> R.string.weight_placeholder_lb
                },
            )
        }
        return context.getString(
            when (unit) {
                UnitSystem.METRIC -> R.string.weight_kg_value
                UnitSystem.IMPERIAL -> R.string.weight_lb_value
            },
            weightToDisplay(weightKg, unit),
        )
    }

    fun formatHeight(heightCm: Double, unit: UnitSystem): String = when (unit) {
        UnitSystem.METRIC -> String.format("%.0f cm", heightCm)
        UnitSystem.IMPERIAL -> {
            val totalInches = heightCm / CM_PER_INCH
            val feet = (totalInches / 12).toInt()
            val inches = (totalInches % 12).roundToInt().coerceIn(0, 11)
            "$feet' $inches\""
        }
    }

    fun weightLabelRes(unit: UnitSystem): Int = when (unit) {
        UnitSystem.METRIC -> R.string.weight_kg
        UnitSystem.IMPERIAL -> R.string.weight_lb
    }

    fun portionLabelRes(unit: UnitSystem): Int = when (unit) {
        UnitSystem.METRIC -> R.string.portion_size_g
        UnitSystem.IMPERIAL -> R.string.portion_size_oz
    }

    fun chartWeightLabelRes(unit: UnitSystem): Int = when (unit) {
        UnitSystem.METRIC -> R.string.weight_chart_metric
        UnitSystem.IMPERIAL -> R.string.weight_chart_imperial
    }
}
