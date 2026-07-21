package com.example.calories.data.network.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.calories.BuildConfig
import com.example.calories.data.network.GeminiApiException
import com.example.calories.data.network.NetworkConnectivity
import com.example.calories.model.FoodAnalysisResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAnalysisService @Inject constructor(
    private val networkConnectivity: NetworkConnectivity,
) {

    private val httpClient by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap): FoodAnalysisResult = withContext(Dispatchers.IO) {
        if (!networkConnectivity.isConnected()) {
            throw GeminiApiException.NoInternet()
        }

        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap is empty" }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey.contains("YOUR")) {
            error("Set GEMINI_API_KEY in local.properties (Google AI Studio key starting with AIza), then rebuild")
        }

        try {
            analyzeFoodImageInternal(bitmap, apiKey)
        } catch (e: GeminiApiException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw GeminiApiException.Timeout()
        } catch (e: SocketTimeoutException) {
            throw GeminiApiException.Timeout()
        } catch (e: UnknownHostException) {
            throw GeminiApiException.NetworkError(e)
        } catch (e: IOException) {
            throw GeminiApiException.NetworkError(e)
        }
    }

    private suspend fun analyzeFoodImageInternal(bitmap: Bitmap, apiKey: String): FoodAnalysisResult {

        val prompt = """
            You are a food and drink recognition system.
            First, determine whether the image clearly shows food or a drink.
            Return JSON only using these field names: is_food, food_name, calories.
            If the image is NOT food or drink, return exactly:
            {"is_food": false, "food_name": "None", "calories": 0}
            If the image IS food or drink, return is_food true, a concise food_name (under 60 characters),
            calories for the full visible serving, and also estimate:
            estimatedWeightGrams (integer), proteinGrams, carbsGrams, fatGrams (decimals), and ingredients (string array).
            Use integers for weight and calories, decimals for macros.
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

        val responseText = withTimeout(REQUEST_TIMEOUT_MS) {
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

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                throw GeminiApiException.ApiError(response.status.value, body)
            }

            response.bodyAsText()
        }

        val modelText = extractModelText(responseText)
            ?.let { extractJsonObject(it) }
            ?: throw IllegalStateException("Gemini returned an empty response")

        Log.d("GeminiDebug", "Model JSON: $modelText")

        return runCatching { parseFoodAnalysisResult(modelText) }.getOrElse {
            throw IllegalStateException("Parse error: $modelText", it)
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 15_000L
    }

    private fun foodAnalysisSchema(): JSONObject {
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("is_food", JSONObject().put("type", "BOOLEAN"))
                    .put("food_name", JSONObject().put("type", "STRING"))
                    .put("calories", JSONObject().put("type", "INTEGER"))
                    .put("estimatedWeightGrams", JSONObject().put("type", "INTEGER"))
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
                    .put("is_food")
                    .put("food_name")
                    .put("calories"),
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
        val isFood = json.optBoolean("is_food", json.optBoolean("isFood", true))
        val foodName = json.optString("food_name", json.optString("foodName", "Unknown meal"))
        val calories = json.optInt("calories", 0)

        if (!isFood || foodName.equals("None", ignoreCase = true)) {
            return FoodAnalysisResult(
                isFood = false,
                foodName = "None",
                calories = 0,
            )
        }

        return FoodAnalysisResult(
            isFood = true,
            foodName = foodName,
            calories = calories,
            estimatedWeightGrams = json.optInt("estimatedWeightGrams", 0),
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
