package com.example.calories.ui.common

/**
 * Shared UI load/result wrapper for feature screens (nutrition, weight, camera).
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
