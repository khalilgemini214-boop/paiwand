package com.paiwand.translate.translate

import com.paiwand.translate.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

        val raw = execute(request)

        val parsed = runCatching {
            json.decodeFromString(ChatResponse.serializer(), raw)
        }.getOrElse { throw TranslationException("Unexpected response from the server") }

        val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (content.isBlank()) throw TranslationException("The model returned an empty translation")
        sanitize(content)
    }

    /**
     * Same request, but reads the response as it arrives so the first words appear immediately
     * instead of after a two or three second pause.
     */
    suspend fun translateStreaming(
        text: String,
        source: Language,
        target: Language,
        apiKey: String,
        baseUrl: String,
        model: String,
        onUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw TranslationException("Add your API key in Settings")

        val sourceName = if (source.code == "auto") "the detected language" else source.name
        val body = ChatRequest(
            model = model.ifBlank { "deepseek-chat" },
            messages = listOf(
                ChatMessage("system", systemPrompt(sourceName, target.name)),
                ChatMessage("user", text)
            ),
            stream = true
        )

        val request = Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val payload = response.body?.string().orEmpty()
                throw TranslationException(describeError(response.code, payload))
            }

            val stream = response.body?.source()
                ?: throw TranslationException("Empty response from the server")

            val builder = StringBuilder()
            while (!stream.exhausted()) {
                val line = stream.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break

                val piece = parseDelta(payload) ?: continue
                builder.append(piece)
                onUpdate(builder.toString())
            }

            val content = builder.toString().trim()
            if (content.isBlank()) throw TranslationException("The model returned an empty translation")
            sanitize(content)
        }
    }

    private fun parseDelta(payload: String): String? = runCatching {
        val choices = json.parseToJsonElement(payload).jsonObject["choices"]?.jsonArray
            ?: return@runCatching null
        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
            ?: return@runCatching null
        delta["content"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /**
     * Runs the call, retrying briefly on 429/5xx. Providers like Gemini return a 503 for
     * "model overloaded", which is transient often enough to be worth two more attempts.
     */
    private fun execute(request: Request): String {
        var last: TranslationException? = null

        for (attempt in 0..2) {
            if (attempt > 0) {
                Thread.sleep(if (attempt == 1) 700L else 1800L)
            }

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw TranslationException("Network problem. Check your connection.")
            }

            response.use {
                val payload = it.body?.string().orEmpty()
                if (it.isSuccessful) return payload

                val error = TranslationException(describeError(it.code, payload))
                val retryable = it.code == 429 || it.code in 500..599
                if (!retryable || attempt == 2) throw error
                last = error
            }
        }

        throw last ?: TranslationException("Translation failed")
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

    /**
     * Providers all shape their errors differently — Gemini even returns a one-element JSON
     * array. Pull whatever message we can find so the user sees the provider's own words
     * instead of a vague "service is busy".
     */
    private fun providerMessage(payload: String): String? = runCatching {
        findMessage(json.parseToJsonElement(payload))
    }.getOrNull()?.trim()?.take(180)?.takeIf { it.isNotEmpty() }

    private fun findMessage(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            element["message"]?.jsonPrimitive?.contentOrNull
                ?: element["error"]?.let { findMessage(it) }
        }

        is JsonArray -> element.firstOrNull()?.let { findMessage(it) }
        else -> null
    }

    private fun describeError(code: Int, payload: String): String {
        val detail = providerMessage(payload)

        val base = when (code) {
            400 -> "Request rejected."
            401, 403 -> "API key rejected. Check it in Settings."
            402 -> "No balance on this API account."
            404 -> "Model not found. Check the model name."
            429 -> "Rate limited. Wait a moment and try again."
            in 500..599 -> "Provider is overloaded right now."
            else -> "Request failed ($code)."
        }

        return if (detail.isNullOrBlank()) base else "$base $detail"
    }
}
