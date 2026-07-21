package com.example.calories.ui.common

import com.example.calories.R
import com.example.calories.data.network.GeminiApiException

fun mapGeminiErrorToUiEvent(error: Throwable): UiEvent {
    return when (error) {
        is GeminiApiException.NoInternet -> UiEvent.MessageRes(R.string.error_no_internet)
        is GeminiApiException.Timeout -> UiEvent.MessageRes(R.string.error_network_timeout)
        is GeminiApiException.NetworkError -> UiEvent.MessageRes(R.string.error_network_connection)
        is GeminiApiException.ApiError -> UiEvent.MessageRes(R.string.error_analyze_food)
        else -> UiEvent.MessageRes(R.string.error_analyze_food)
    }
}
