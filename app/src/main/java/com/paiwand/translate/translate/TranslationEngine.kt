package com.paiwand.translate.translate

import com.paiwand.translate.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TranslationException(message: String) : Exception(message)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 1400,
    val stream: Boolean = false
)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

class TranslationEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        source: Language,
        target: Language,
        apiKey: String,
        baseUrl: String,
        model: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw TranslationException("Add your API key in Settings")

        val sourceName = if (source.code == "auto") "the detected language" else source.name
        val body = ChatRequest(
            model = model.ifBlank { "deepseek-chat" },
            messages = listOf(
                ChatMessage("system", systemPrompt(sourceName, target.name)),
                ChatMessage("user", text)
            )
        )

        val request = Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(mediaType))
            .build()

        val raw = try {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw TranslationException(describeError(response.code, payload))
                }
                payload
            }
        } catch (e: IOException) {
            throw TranslationException("Network problem. Check your connection.")
        }

        val parsed = runCatching {
            json.decodeFromString(ChatResponse.serializer(), raw)
        }.getOrElse { throw TranslationException("Unexpected response from the server") }

        val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (content.isBlank()) throw TranslationException("The model returned an empty translation")
        sanitize(content)
    }

    private fun endpoint(base: String): String {
        val clean = base.trim().trimEnd('/')
        if (clean.isEmpty()) throw TranslationException("API base URL is empty")
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }

    private fun systemPrompt(source: String, target: String) = """
        You are a professional translator.
        Translate the user's text from $source into ${target}.

        Rules:
        - Output ONLY the translation. No notes, no quotes, no explanations, no romanization, no labels.
        - Preserve line breaks, numbers, punctuation and formatting exactly.
        - Keep names, brands, URLs and code unchanged.
        - If the text is already in ${target}, return it unchanged.
        - Never refuse and never add commentary.
    """.trimIndent()

    private fun sanitize(value: String): String {
        var out = value.trim()
        val prefixes = listOf(
            "Translation:", "Translated:", "Here is the translation:", "Here's the translation:"
        )
        for (prefix in prefixes) {
            if (out.startsWith(prefix, ignoreCase = true)) {
                out = out.removePrefix(prefix).trim()
            }
        }
        if (out.length > 1 && out.startsWith("\"") && out.endsWith("\"")) {
            out = out.substring(1, out.length - 1).trim()
        }
        return out
    }

    private fun describeError(code: Int, payload: String): String = when (code) {
        401, 403 -> "API key rejected. Check it in Settings."
        402 -> "Your API account has no balance."
        404 -> "Endpoint not found. Check the API base URL."
        429 -> "Rate limited. Try again in a moment."
        in 500..599 -> "Translation service is busy. Try again."
        else -> "Request failed ($code). ${payload.take(140)}".trim()
    }
}
