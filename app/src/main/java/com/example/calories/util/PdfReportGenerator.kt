package com.example.calories.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.calories.model.NutritionReportData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 48f
    private const val MARGIN_TOP = 56f
    private const val LINE_GAP = 8f

    private val colorPrimary = Color.parseColor("#6B8355")
    private val colorTextPrimary = Color.parseColor("#212121")
    private val colorTextSecondary = Color.parseColor("#757575")
    private val colorDivider = Color.parseColor("#E8EEE3")
    private val colorSurface = Color.parseColor("#F7FAF4")

    fun generate(context: Context, data: NutritionReportData): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawReport(page.canvas, data)
        document.finishPage(page)

        val dir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files directory unavailable")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Could not create report directory")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "nutrition_report_$timestamp.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawReport(canvas: Canvas, data: NutritionReportData) {
        var y = MARGIN_TOP

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimary
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextPrimary
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSecondary
            textSize = 11f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextPrimary
            textSize = 12f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextPrimary
            textSize = 11f
        }
        val dividerPaint = Paint().apply { color = colorDivider }
        val cardPaint = Paint().apply { color = colorSurface }

        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, cardPaint)
        canvas.drawText("Calories", MARGIN_H, 36f, titlePaint.apply { textSize = 18f })
        canvas.drawText(
            "NUTRITION & HEALTH REPORT",
            MARGIN_H,
            68f,
            titlePaint.apply { textSize = 20f },
        )
        y = 112f

        y = drawSectionTitle(canvas, "User Profile", sectionPaint, y)
        y = drawKeyValue(canvas, "Full Name", data.userName, labelPaint, valuePaint, y)
        y = drawKeyValue(canvas, "Date Range", data.dateRangeText, labelPaint, valuePaint, y)
        y = drawDivider(canvas, dividerPaint, y + 12f)


        y = drawSectionTitle(canvas, "1. Calorie Overview", sectionPaint, y + 8f)
        y = drawMetricCard(
            canvas,
            cardPaint,
            listOf(
                "Daily Calorie Target" to "${data.targetCaloriesPerDay} kcal",
                "Avg Daily Intake" to "${data.avgCaloriesIntake} kcal",
            ),
            labelPaint,
            valuePaint,
            y,
        )
        val calorieDelta = data.avgCaloriesIntake - data.targetCaloriesPerDay
        val deltaText = when {
            data.targetCaloriesPerDay <= 0 -> "Calorie target not set"
            calorieDelta > 0 -> "Exceeded target by $calorieDelta kcal/day on average"
            calorieDelta < 0 -> "Below target by ${-calorieDelta} kcal/day on average"
            else -> "On track with daily calorie target"
        }
        y = drawBodyLine(canvas, deltaText, bodyPaint, y + 8f)
        y = drawDivider(canvas, dividerPaint, y + 16f)

        y = drawSectionTitle(canvas, "2. Average Macros & Hydration", sectionPaint, y + 8f)
        y = drawMetricCard(
            canvas,
            cardPaint,
            listOf(
                "Protein" to formatGrams(data.avgProteinGrams),
                "Carbohydrates" to formatGrams(data.avgCarbsGrams),
                "Fat" to formatGrams(data.avgFatGrams),
                "Total Water Intake" to "${data.totalWaterMl} ml",
            ),
            labelPaint,
            valuePaint,
            y,
        )

        // Footer disclaimer
        val footerY = PAGE_HEIGHT - 72f
        canvas.drawLine(MARGIN_H, footerY - 16f, PAGE_WIDTH - MARGIN_H, footerY - 16f, dividerPaint)
        val disclaimer = (
                "Disclaimer: This report is generated for informational and tracking purposes only and " +
                        "does not constitute medical advice. Please consult a qualified nutritionist or physician " +
                        "before making major dietary changes."
                )
        drawWrappedText(canvas, disclaimer, bodyPaint, MARGIN_H, footerY, PAGE_WIDTH - MARGIN_H * 2)
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        title: String,
        paint: Paint,
        y: Float,
    ): Float {
        canvas.drawText(title, MARGIN_H, y, paint)
        return y + 24f
    }

    private fun drawKeyValue(
        canvas: Canvas,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint,
        y: Float,
    ): Float {
        canvas.drawText(label, MARGIN_H, y, labelPaint)
        canvas.drawText(value, MARGIN_H, y + 16f, valuePaint)
        return y + 36f
    }

    private fun drawMetricCard(
        canvas: Canvas,
        cardPaint: Paint,
        rows: List<Pair<String, String>>,
        labelPaint: Paint,
        valuePaint: Paint,
        startY: Float,
    ): Float {
        val cardHeight = rows.size * 28f + 24f
        canvas.drawRoundRect(
            MARGIN_H,
            startY,
            PAGE_WIDTH - MARGIN_H,
            startY + cardHeight,
            12f,
            12f,
            cardPaint,
        )
        var y = startY + 24f
        rows.forEach { (label, value) ->
            canvas.drawText(label, MARGIN_H + 16f, y, labelPaint)
            val valueWidth = valuePaint.measureText(value)
            canvas.drawText(value, PAGE_WIDTH - MARGIN_H - 16f - valueWidth, y, valuePaint)
            y += 28f
        }
        return startY + cardHeight + LINE_GAP
    }

    private fun drawBodyLine(canvas: Canvas, text: String, paint: Paint, y: Float): Float {
        canvas.drawText(text, MARGIN_H, y, paint)
        return y + 18f
    }

    private fun drawDivider(canvas: Canvas, paint: Paint, y: Float): Float {
        canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, paint)
        return y + 8f
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        maxWidth: Float,
    ) {
        val words = text.split(' ')
        var line = StringBuilder()
        var currentY = y
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, currentY, paint)
                currentY += 14f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, currentY, paint)
        }
    }

    private fun formatGrams(value: Double): String =
        String.format(Locale.US, "%.1f g/day", value)
}