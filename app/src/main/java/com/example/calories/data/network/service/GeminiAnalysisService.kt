package com.example.calories.data.network.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.calories.BuildConfig
import com.example.calories.model.FoodAnalysisResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAnalysisService @Inject constructor() {

    private val httpClient by lazy { HttpClient(Android) }

    suspend fun analyzeFoodImage(bitmap: Bitmap): FoodAnalysisResult = withContext(Dispatchers.IO) {

        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap is empty" }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey.contains("YOUR")) {
            error("Set GEMINI_API_KEY in local.properties (Google AI Studio key starting with AIza), then rebuild")
        }

        val prompt = """
            You are a food and nutrition analysis system.
            Analyze the dish in the image and estimate the nutrition for the full visible serving.
            Return JSON only matching FoodAnalysisResult.
            Use integers for weight and calories, decimals for macros.
            Keep foodName concise (under 60 characters).
        """.trimIndent()

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", "image/jpeg")
                                        .put("data", bitmap.toJpegBase64()),
                                ),
                            ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", foodAnalysisSchema())
                    .put(
                        "thinkingConfig",
                        JSONObject().put("thinkingLevel", "minimal"),
                    ),
            )
            .toString()

        val response = httpClient.post {
            url {
                protocol = io.ktor.http.URLProtocol.HTTPS
                host = "generativelanguage.googleapis.com"
                encodedPath = "/v1beta/models/gemini-3.5-flash:generateContent"
            }
            header("x-goog-api-key", apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val responseText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Gemini API error ${response.status.value}: $responseText")
        }

        val modelText = extractModelText(responseText)
            ?.let { extractJsonObject(it) }
            ?: throw IllegalStateException("Gemini returned an empty response")

        Log.d("GeminiDebug", "Model JSON: $modelText")

        runCatching { parseFoodAnalysisResult(modelText) }.getOrElse {
            throw IllegalStateException("Parse error: $modelText", it)
        }
    }

    private fun foodAnalysisSchema(): JSONObject {
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("foodName", JSONObject().put("type", "STRING"))
                    .put("estimatedWeightGrams", JSONObject().put("type", "INTEGER"))
                    .put("calories", JSONObject().put("type", "INTEGER"))
                    .put("proteinGrams", JSONObject().put("type", "NUMBER"))
                    .put("carbsGrams", JSONObject().put("type", "NUMBER"))
                    .put("fatGrams", JSONObject().put("type", "NUMBER"))
                    .put(
                        "ingredients",
                        JSONObject()
                            .put("type", "ARRAY")
                            .put("items", JSONObject().put("type", "STRING")),
                    ),
            )
            .put(
                "required",
                JSONArray()
                    .put("foodName")
                    .put("estimatedWeightGrams")
                    .put("calories")
                    .put("proteinGrams")
                    .put("carbsGrams")
                    .put("fatGrams")
                    .put("ingredients"),
            )
    }

    private fun extractModelText(responseText: String): String? {
        val parts = JSONObject(responseText)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return null

        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // Skip thought-only parts; keep real model text.
                if (part.optBoolean("thought", false)) continue
                val chunk = part.optString("text")
                if (chunk.isNotBlank()) append(chunk)
            }
        }.trim()

        return text.ifBlank { null }
    }

    /**
     * Pulls the first JSON object from model text and closes truncated braces/brackets if needed.
     * Gemini 3 thinking models sometimes attach thoughtSignature metadata and can truncate JSON in logs.
     */
    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = cleaned.indexOf('{')
        require(start >= 0) { "No JSON object in model response" }

        val candidate = cleaned.substring(start)
        runCatching { JSONObject(candidate); return candidate }

        val repaired = repairTruncatedJson(candidate)
        JSONObject(repaired) // validate
        return repaired
    }

    private fun repairTruncatedJson(json: String): String {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escape = false

        for (c in json) {
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> stack.addLast('}')
                c == '[' -> stack.addLast(']')
                c == '}' || c == ']' -> {
                    if (stack.isNotEmpty() && stack.last() == c) stack.removeLast()
                }
            }
        }

        if (inString) {
            return repairTruncatedJson(json + "\"")
        }

        return json + stack.reversed().joinToString("")
    }

    private fun parseFoodAnalysisResult(rawJson: String): FoodAnalysisResult {
        val json = JSONObject(rawJson)
        return FoodAnalysisResult(
            foodName = json.optString("foodName", "Unknown meal"),
            estimatedWeightGrams = json.optInt("estimatedWeightGrams", 0),
            calories = json.optInt("calories", 0),
            proteinGrams = json.optDouble("proteinGrams", 0.0).toFloat(),
            carbsGrams = json.optDouble("carbsGrams", 0.0).toFloat(),
            fatGrams = json.optDouble("fatGrams", 0.0).toFloat(),
            ingredients = json.optJSONArray("ingredients")?.let { arr ->
                List(arr.length()) { i -> arr.getString(i) }
            } ?: emptyList(),
        )
    }

    private fun Bitmap.toJpegBase64(): String {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
