package com.example.calories.insights

import androidx.annotation.StringRes
import com.example.calories.R

object ProgressInsightUiMapper {
    @StringRes
    fun titleRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_title
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_title
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_title
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_title
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_title
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_title
        else -> R.string.insights_section_title
    }

    @StringRes
    fun bodyRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_body
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_body
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_body
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_body
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_body
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_body
        else -> R.string.insights_insufficient_body
    }
}
