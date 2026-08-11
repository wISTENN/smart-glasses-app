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

class GeminiApiClient(
    private val apiKey: String
) {
    private val client = OkHttpClient()

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
                                    .put("text", "Слушай это аудио и расшифруй речь на русском языке. Верни только текст, без пояснений.")
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

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiApiClient", "HTTP error: ${response.code} ${response.message}")
                    return "Ошибка распознавания речи"
                }

                val responseText = response.body?.string() ?: "{}"
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return "Не удалось распознать речь"

                val firstCandidate = candidates.optJSONObject(0) ?: return "Не удалось распознать речь"
                val content = firstCandidate.optJSONObject("content") ?: return "Не удалось распознать речь"
                val parts = content.optJSONArray("parts") ?: return "Не удалось распознать речь"

                val builder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) builder.append(text)
                }

                builder.toString().ifEmpty { "Не удалось распознать речь" }
            }
        } catch (e: Exception) {
            Log.e("GeminiApiClient", "Gemini request failed", e)
            "Ошибка сети при обращении к Gemini"
        }
    }
}
