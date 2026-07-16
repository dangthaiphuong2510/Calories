package com.example.calories.ui.weight

import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

object CalorieTrendChartHelper {

    // (barWidth + barSpace) * datasetCount + groupSpace = 1.0
    private const val GROUP_SPACE = 0.16f
    private const val BAR_SPACE = 0.04f
    private const val BAR_WIDTH = 0.38f

    fun bind(
        chart: BarChart,
        points: List<DailyCaloriePoint>,
        period: NutritionPeriod,
        consumedColor: Int,
        burnedColor: Int,
        consumedLabel: String,
        burnedLabel: String,
        emptyMessage: String,
    ) {
        if (points.isEmpty() || points.all { it.consumedKcal == 0 && it.burnedKcal == 0 }) {
            chart.clear()
            chart.xAxis.valueFormatter = null
            chart.setNoDataText(emptyMessage)
            chart.invalidate()
            return
        }

        when (period) {
            NutritionPeriod.DAY -> bindDayChart(
                chart = chart,
                point = points.first(),
                consumedColor = consumedColor,
                burnedColor = burnedColor,
                consumedLabel = consumedLabel,
                burnedLabel = burnedLabel,
            )
            NutritionPeriod.WEEK -> bindWeekChart(
                chart = chart,
                points = points,
                consumedColor = consumedColor,
                burnedColor = burnedColor,
                consumedLabel = consumedLabel,
                burnedLabel = burnedLabel,
            )
        }
    }

    private fun bindDayChart(
        chart: BarChart,
        point: DailyCaloriePoint,
        consumedColor: Int,
        burnedColor: Int,
        consumedLabel: String,
        burnedLabel: String,
    ) {
        val entries = listOf(
            BarEntry(0f, point.consumedKcal.toFloat()),
            BarEntry(1f, point.burnedKcal.toFloat()),
        )
        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(consumedColor, burnedColor)
            setDrawValues(true)
            valueTextColor = Color.GRAY
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    value.toInt().toString()
            }
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.45f }
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setFitBars(true)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.GRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.GRAY
                setCenterAxisLabels(false)
                axisMinimum = -0.5f
                axisMaximum = 1.5f
                valueFormatter = IndexAxisValueFormatter(listOf(consumedLabel, burnedLabel))
                labelCount = 2
            }
            legend.isEnabled = false
            invalidate()
        }
    }

    private fun bindWeekChart(
        chart: BarChart,
        points: List<DailyCaloriePoint>,
        consumedColor: Int,
        burnedColor: Int,
        consumedLabel: String,
        burnedLabel: String,
    ) {
        val consumedEntries = points.mapIndexed { index, point ->
            BarEntry(index.toFloat(), point.consumedKcal.toFloat())
        }
        val burnedEntries = points.mapIndexed { index, point ->
            BarEntry(index.toFloat(), point.burnedKcal.toFloat())
        }
        val labels = points.map { it.label }

        val consumedSet = BarDataSet(consumedEntries, consumedLabel).apply {
            color = consumedColor
            setDrawValues(false)
        }
        val burnedSet = BarDataSet(burnedEntries, burnedLabel).apply {
            color = burnedColor
            setDrawValues(false)
        }

        val barData = BarData(consumedSet, burnedSet).apply {
            barWidth = BAR_WIDTH
        }

        chart.apply {
            data = barData
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setFitBars(true)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.GRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        value.toInt().toString()
                }
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.GRAY
                valueFormatter = IndexAxisValueFormatter(labels)
                setCenterAxisLabels(true)
                axisMinimum = 0f
                axisMaximum = 0f + barData.getGroupWidth(GROUP_SPACE, BAR_SPACE) * points.size
                labelCount = points.size
            }
            legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                textColor = Color.GRAY
            }
            groupBars(0f, GROUP_SPACE, BAR_SPACE)
            invalidate()
        }
    }
}
