package com.smartglasses.app.network

import android.util.Base64
import android.util.Log
import com.smartglasses.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendAudioToGemini(base64Pcm: String, sampleRate: Int): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "GEMINI_API_KEY is empty")
            return@withContext "Ошибка: API ключ не найден"
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(
                                JSONObject().put(
                                    "text",
                                    "Это аудиосообщение от пользователя. Послушай его и дай короткий, четкий и понятный ответ на русском языке для озвучки в наушник."
                                )
                            )
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "audio/pcm")
                                        .put("data", base64Pcm)
                                )
                            )
                    )
                )
            )
        }

        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code} ${response.message}")
                    return@withContext "Ошибка ответа от Gemini (${response.code})"
                }

                val responseText = response.body?.string() ?: "{}"
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return@withContext "Не удалось обработать ответ"

                val firstCandidate = candidates.optJSONObject(0) ?: return@withContext "Не удалось обработать ответ"
                val content = firstCandidate.optJSONObject("content") ?: return@withContext "Не удалось обработать ответ"
                val parts = content.optJSONArray("parts") ?: return@withContext "Не удалось обработать ответ"

                val builder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) builder.append(text)
                }

                builder.toString().ifEmpty { "Пустой ответ от ассистента" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini request failed", e)
            "Ошибка сети при обращении к Gemini"
        }
    }
}
