package com.example.calories.ui.weight

import android.graphics.Color
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

object MacroDistributionChartHelper {

    fun bind(
        chart: PieChart,
        distribution: MacroDistributionUi,
        proteinColor: Int,
        carbColor: Int,
        fatColor: Int,
        emptyMessage: String,
    ) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(false)
            holeRadius = 58f
            transparentCircleRadius = 62f
            setHoleColor(Color.TRANSPARENT)
            setDrawCenterText(true)
            setTouchEnabled(true)
        }

        if (!distribution.hasData) {
            chart.clear()
            chart.centerText = emptyMessage
            chart.setNoDataText(emptyMessage)
            chart.invalidate()
            return
        }

        val entries = buildList {
            if (distribution.proteinGrams > 0) {
                add(PieEntry(distribution.proteinGrams.toFloat(), "Protein"))
            }
            if (distribution.carbGrams > 0) {
                add(PieEntry(distribution.carbGrams.toFloat(), "Carbs"))
            }
            if (distribution.fatGrams > 0) {
                add(PieEntry(distribution.fatGrams.toFloat(), "Fat"))
            }
        }

        val colors = buildList {
            if (distribution.proteinGrams > 0) add(proteinColor)
            if (distribution.carbGrams > 0) add(carbColor)
            if (distribution.fatGrams > 0) add(fatColor)
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(false)
            sliceSpace = 2f
        }

        chart.centerText = distribution.centerLabel.ifBlank { "—" }
        chart.data = PieData(dataSet)
        chart.invalidate()
    }
}
