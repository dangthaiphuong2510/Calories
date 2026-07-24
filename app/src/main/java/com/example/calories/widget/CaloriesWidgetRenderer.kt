package com.example.calories.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.calories.R
import com.example.calories.insights.ProgressInsightUiMapper

object CaloriesWidgetRenderer {

    fun render(context: Context, snapshot: CaloriesWidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calories_home)

        when (snapshot.displayMode) {
            CaloriesWidgetDisplayMode.SIGNED_OUT -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.VISIBLE)
                views.setViewVisibility(R.id.sectionProgress, View.GONE)
                views.setViewVisibility(R.id.sectionWater, View.GONE)
                views.setViewVisibility(R.id.sectionInsight, View.GONE)
                views.setTextViewText(
                    R.id.tvWidgetMessage,
                    context.getString(R.string.widget_sign_in_prompt),
                )
            }
            CaloriesWidgetDisplayMode.NO_GOAL -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.VISIBLE)
                views.setViewVisibility(R.id.sectionProgress, View.GONE)
                views.setViewVisibility(R.id.sectionWater, View.GONE)
                views.setViewVisibility(R.id.sectionInsight, View.GONE)
                views.setTextViewText(
                    R.id.tvWidgetMessage,
                    context.getString(R.string.widget_no_goal_prompt),
                )
            }
            CaloriesWidgetDisplayMode.READY -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.GONE)
                views.setViewVisibility(R.id.sectionProgress, View.VISIBLE)

                views.setTextViewText(R.id.tvRemaining, snapshot.caloriesRemaining.toString())
                views.setTextViewText(R.id.tvGoal, snapshot.dailyGoal.toString())
                views.setTextViewText(R.id.tvEaten, snapshot.totalEaten.toString())
                views.setProgressBar(R.id.progressCalories, 100, snapshot.progressPercent, false)

                views.setViewVisibility(R.id.sectionWater, View.VISIBLE)
                views.setTextViewText(
                    R.id.tvWaterProgress,
                    context.getString(
                        R.string.water_progress_format,
                        snapshot.waterIntakeMl,
                        snapshot.waterGoalMl,
                    ),
                )
                views.setProgressBar(R.id.progressWater, 100, snapshot.waterProgressPercent, false)

                val insight = snapshot.activeInsight
                if (insight == null) {
                    views.setViewVisibility(R.id.sectionInsight, View.GONE)
                } else {
                    views.setViewVisibility(R.id.sectionInsight, View.VISIBLE)
                    views.setTextViewText(
                        R.id.tvInsightTitle,
                        context.getString(ProgressInsightUiMapper.titleRes(insight.id)),
                    )
                    val bodyRes = ProgressInsightUiMapper.bodyRes(insight.id)
                    val body = if (insight.formatArgs.isEmpty()) {
                        context.getString(bodyRes)
                    } else {
                        context.getString(bodyRes, *insight.formatArgs.toTypedArray())
                    }
                    views.setTextViewText(R.id.tvInsightBody, body)
                }
            }
        }

        return views
    }
}
