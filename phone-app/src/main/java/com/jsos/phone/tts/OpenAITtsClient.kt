package com.jsos.phone.tts

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * OpenAI speech API client for text-to-speech synthesis.
 */
class OpenAITtsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun synthesize(
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        speed: Double = 1.0
    ): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val requestBody = OpenAISpeechRequest(
                model = model,
                input = text,
                voice = voice,
                responseFormat = "mp3",
                speed = speed.coerceIn(0.25, 4.0)
            )

            val request = Request.Builder()
                .url("$BASE_URL/audio/speech")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    Exception("OpenAI TTS synthesis failed: ${response.code} - $errorBody")
                )
            }

            val inputStream = response.body?.byteStream()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            Result.success(inputStream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val BASE_URL = "https://api.openai.com/v1"
    }
}

private data class OpenAISpeechRequest(
    @SerializedName("model") val model: String,
    @SerializedName("input") val input: String,
    @SerializedName("voice") val voice: String,
    @SerializedName("response_format") val responseFormat: String = "mp3",
    @SerializedName("speed") val speed: Double = 1.0
)
