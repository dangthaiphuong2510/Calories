package com.example.calories.insights

object ProgressInsightThresholds {
    const val WINDOW_DAYS = 7L
    const val PLATEAU_WEIGHT_LOOKBACK_DAYS = 14L
    const val LOGGED_LOOKBACK_DAYS = 14L
    const val MIN_FOOD_LOG_DAYS = 3
    const val UNDER_TARGET_DAYS_NEEDED = 3
    const val UNDER_TARGET_LOGGED_SAMPLE = 5
    const val FLAT_WEIGHT_MAX_ABS_DELTA_KG = 0.4
    const val WEEKEND_SPIKE_RATIO = 1.15
    const val PROTEIN_RATIO_THRESHOLD = 0.80
    const val PROTEIN_SHORTFALL_DAYS_NEEDED = 4
    const val PROTEIN_LOGGED_SAMPLE = 5
    const val LOGGING_GAP_MISSING_DAYS = 2
    const val ON_TRACK_DAYS_NEEDED = 5
    const val ON_TRACK_TOLERANCE = 0.10
    const val MIN_WEIGHT_POINTS_FOR_PLATEAU = 2
    const val MAX_PROGRESS_INSIGHTS = 3
}
