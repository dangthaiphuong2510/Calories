package com.example.calories.data.remote.gemini

import com.example.calories.BuildConfig
import com.example.calories.model.FoodAnalysisResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Food image analysis via Gemini Vision.
 * Uses a deterministic mock when [BuildConfig.GEMINI_API_KEY] is not configured,
 * so camera UI can be developed without a live key.
 */
@Singleton
class FoodAnalysisService @Inject constructor() {

    suspend fun analyzeFoodImage(imageBytes: ByteArray): FoodAnalysisResult {
        require(imageBytes.isNotEmpty()) { "Image is empty" }

        // Placeholder until Gemini Vision client is wired with a real API key.
        if (BuildConfig.GEMINI_API_KEY.isBlank() ||
            BuildConfig.GEMINI_API_KEY == "YOUR_GEMINI_API_KEY"
        ) {
            delay(800)
            return FoodAnalysisResult(
                name = "Grilled chicken salad",
                calories = 420,
                protein = 38.0,
                carb = 18.0,
                fat = 22.0,
            )
        }

        // TODO: Call Gemini Vision with imageBytes + nutrition prompt.
        delay(800)
        return FoodAnalysisResult(
            name = "Detected meal",
            calories = 350,
            protein = 20.0,
            carb = 30.0,
            fat = 12.0,
        )
    }
}
