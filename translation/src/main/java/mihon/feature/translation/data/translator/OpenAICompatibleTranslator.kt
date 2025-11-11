package mihon.feature.translation.data.translator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.feature.translation.domain.TranslatorAPI
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.TranslationResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI-compatible API translator implementation
 *
 * Supports:
 * - OpenAI (GPT-4, GPT-3.5, etc.)
 * - DeepSeek (deepseek-chat, deepseek-coder)
 * - Anthropic Claude (via OpenAI-compatible endpoint)
 * - Local LLMs (Ollama, LM Studio, etc.)
 * - Any service using OpenAI's chat completions API format
 *
 * Configuration:
 * - baseUrl: API endpoint (e.g., "https://api.deepseek.com", "https://api.openai.com")
 * - modelName: Model identifier (e.g., "deepseek-chat", "gpt-4", "claude-3-sonnet")
 * - apiKey: Authentication key (may be empty for local LLMs)
 */
class OpenAICompatibleTranslator(
    private val baseUrl: String,
    private val modelName: String,
    private val apiKey: String,
    private val client: OkHttpClient,
) : TranslatorAPI {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun translate(
        texts: List<String>,
        from: Language,
        to: Language,
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        // Validate configuration
        val validationError = validateConfiguration()
        if (validationError != null) {
            return@withContext texts.map {
                TranslationResult(
                    originalText = it,
                    translatedText = it,
                    error = validationError,
                )
            }
        }

        // Process in batches of 10 for efficiency
        val batchSize = 10
        val results = mutableListOf<TranslationResult>()

        texts.chunked(batchSize).forEach { batch ->
            try {
                val prompt = buildTranslationPrompt(batch, from, to)
                val response = callOpenAICompatibleAPI(prompt)
                val translations = parseResponse(response, batch)
                results.addAll(translations)
            } catch (e: Exception) {
                Log.e(TAG, "OpenAI-compatible translation failed for batch", e)
                // Fallback: return original text with error
                batch.forEach { text ->
                    results.add(
                        TranslationResult(
                            originalText = text,
                            translatedText = text,
                            error = e.message ?: "Translation failed",
                        ),
                    )
                }
            }
        }

        return@withContext results
    }

    /**
     * Build translation prompt optimized for manga/comic dialogue
     */
    private fun buildTranslationPrompt(
        texts: List<String>,
        from: Language,
        to: Language,
    ): String {
        return """Translate the following comic/manga dialogue from ${from.displayName} to ${to.displayName}.

Instructions:
- Preserve tone, emotion, and personality
- Keep informal speech patterns (slang, dialectical nuances)
- Maintain character voice consistency
- Preserve sound effects and onomatopoeia where appropriate
- Output ONLY the translations, one per line, in the same order

Texts to translate:
${texts.mapIndexed { index, text -> "${index + 1}. $text" }.joinToString("\n")}

Translations:
        """.trimIndent()
    }

    /**
     * Call OpenAI-compatible API endpoint
     */
    private fun callOpenAICompatibleAPI(prompt: String): String {
        val endpoint = normalizeBaseUrl(baseUrl)
        val requestBody = OpenAIChatRequest(
            model = modelName,
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "You are a professional manga and comic translator. " +
                        "Provide accurate, natural translations that preserve the original tone and style.",
                ),
                ChatMessage(
                    role = "user",
                    content = prompt,
                ),
            ),
            temperature = 0.3, // Lower temperature for more consistent translations
            maxTokens = 2000,
        )

        val jsonBody = json.encodeToString(OpenAIChatRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .apply {
                // Add API key if provided (not needed for local LLMs)
                if (apiKey.isNotEmpty()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
                addHeader("Content-Type", "application/json")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("API error (${response.code}): $errorBody")
            }

            return response.body?.string()
                ?: throw Exception("Empty response from API")
        }
    }

    /**
     * Parse OpenAI-compatible API response
     */
    private fun parseResponse(
        responseJson: String,
        originalTexts: List<String>,
    ): List<TranslationResult> {
        try {
            val response = json.decodeFromString<OpenAIChatResponse>(responseJson)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw Exception("No content in response")

            // Parse translations (one per line)
            val translations = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    // Remove leading numbers like "1. ", "2. " etc.
                    line.replace(Regex("^\\d+\\.\\s*"), "")
                }

            // Match translations to original texts
            return originalTexts.mapIndexed { index, originalText ->
                val translatedText = translations.getOrNull(index) ?: originalText
                TranslationResult(
                    originalText = originalText,
                    translatedText = translatedText,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse API response", e)
            throw Exception("Failed to parse translation response: ${e.message}")
        }
    }

    /**
     * Validate configuration
     */
    private fun validateConfiguration(): String? {
        if (baseUrl.isBlank()) {
            return "Base URL is not configured. Please set it in translation settings."
        }

        if (modelName.isBlank()) {
            return "Model name is not configured. Please set it in translation settings."
        }

        // API key is optional for local LLMs
        if (apiKey.isBlank() && !isLocalUrl(baseUrl)) {
            return "API key is required for cloud services. Please set it in translation settings."
        }

        return null
    }

    /**
     * Check if URL is a local endpoint (doesn't require API key)
     */
    private fun isLocalUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("localhost") ||
            normalized.contains("127.0.0.1") ||
            normalized.contains("0.0.0.0") ||
            normalized.contains("192.168.") ||
            normalized.contains("10.0.")
    }

    /**
     * Normalize base URL to chat completions endpoint
     */
    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim().removeSuffix("/")

        // If URL doesn't end with the chat completions path, add it
        if (!normalized.endsWith("/chat/completions")) {
            // Handle common base URLs
            when {
                normalized.endsWith("/v1") -> normalized += "/chat/completions"
                normalized.contains("api.openai.com") && !normalized.contains("/v1") ->
                    normalized += "/v1/chat/completions"
                normalized.contains("api.deepseek.com") && !normalized.contains("/v1") ->
                    normalized += "/v1/chat/completions"
                else -> normalized += "/v1/chat/completions"
            }
        }

        return normalized
    }

    override fun getName(): String = "OpenAI-Compatible ($modelName)"

    override suspend fun isAvailable(): Boolean {
        // Check if configuration is valid
        return validateConfiguration() == null
    }

    override fun supportsLanguagePair(from: Language, to: Language): Boolean {
        // OpenAI-compatible models generally support all major languages
        return true
    }

    companion object {
        private const val TAG = "OpenAICompatTranslator"

        // Common providers and their default configurations
        object Providers {
            const val OPENAI_BASE_URL = "https://api.openai.com/v1"
            const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"
            const val OLLAMA_BASE_URL = "http://localhost:11434/v1"
            const val LM_STUDIO_BASE_URL = "http://localhost:1234/v1"

            const val DEFAULT_MODEL_OPENAI = "gpt-4o-mini"
            const val DEFAULT_MODEL_DEEPSEEK = "deepseek-chat"
            const val DEFAULT_MODEL_OLLAMA = "llama3"
        }
    }
}

/**
 * OpenAI Chat API request format
 */
@Serializable
private data class OpenAIChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
)

@Serializable
private data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
)

/**
 * OpenAI Chat API response format
 */
@Serializable
private data class OpenAIChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice>,
    val usage: Usage? = null,
)

@Serializable
private data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)
