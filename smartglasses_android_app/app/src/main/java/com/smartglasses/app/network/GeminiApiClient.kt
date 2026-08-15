package com.smartglasses.app.network

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiApiClient(
    private val apiKey: String
) {
    // Настраиваем таймауты, чтобы запросы с аудио не обрывались
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun transcribeAudio(file: File): String {
        val audioBytes = file.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("text", "Это аудиосообщение от пользователя. Послушай его и дай короткий, четкий и понятный ответ на русском языке для озвучки в наушник.")
                            )
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "audio/wav")
                                        .put("data", base64Audio)
                                )
                            )
                    )
                )
            )
        }

        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // Исправлена модель на рабочую gemini-1.5-flash
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiApiClient", "HTTP error: ${response.code} ${response.message}")
                    return "Ошибка ответа от Gemini (${response.code})"
                }

                val responseText = response.body?.string() ?: "{}"
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return "Не удалось обработать ответ"

                val firstCandidate = candidates.optJSONObject(0) ?: return "Не удалось обработать ответ"
                val content = firstCandidate.optJSONObject("content") ?: return "Не удалось обработать ответ"
                val parts = content.optJSONArray("parts") ?: return "Не удалось обработать ответ"

                val builder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) builder.append(text)
                }

                builder.toString().ifEmpty { "Пустой ответ от ассистента" }
            }
        } catch (e: Exception) {
            Log.e("GeminiApiClient", "Gemini request failed", e)
            "Ошибка сети при обращении к Gemini"
        }
    }
}
