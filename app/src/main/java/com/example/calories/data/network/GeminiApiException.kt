package com.example.calories.data.network

sealed class GeminiApiException(message: String) : Exception(message) {
    class NoInternet : GeminiApiException("No internet connection")
    class Timeout : GeminiApiException("Request timed out")
    class NetworkError(cause: Throwable) : GeminiApiException(cause.message ?: "Network error")
    class ApiError(val statusCode: Int, body: String) :
        GeminiApiException("Gemini API error $statusCode: $body")
}
