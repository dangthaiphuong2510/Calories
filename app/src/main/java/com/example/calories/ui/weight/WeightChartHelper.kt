package com.example.calories.ui.weight

import android.graphics.Color
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.model.WeightEntry
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.UnitConverter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

object WeightChartHelper {

    fun bind(
        chart: LineChart,
        entries: List<WeightEntry>,
        primaryColor: Int,
        unitSystem: UnitSystem = UnitSystem.METRIC,
        seriesLabel: String = "Weight",
    ) {
        if (entries.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No weight data yet")
            chart.invalidate()
            return
        }

        val chronological = entries.sortedBy { it.recordedAt }
        val points = chronological.mapIndexed { index, item ->
            Entry(
                index.toFloat(),
                UnitConverter.weightToDisplay(item.weightKg, unitSystem).toFloat(),
            )
        }
        val labels = chronological.map { DateTimeUtils.formatChartLabel(it.recordedAt) }

        val dataSet = LineDataSet(points, seriesLabel).apply {
            color = primaryColor
            setCircleColor(primaryColor)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.GRAY
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return labels.getOrElse(index) { "" }
                    }
                }
            }
            axisLeft.textColor = Color.GRAY
            invalidate()
        }
    }
}
