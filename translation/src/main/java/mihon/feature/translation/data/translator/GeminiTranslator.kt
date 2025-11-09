package mihon.feature.translation.data.translator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Gemini API translator implementation
 * Uses Google's Gemini API for high-quality, free translation
 */
class GeminiTranslator(
    private val apiKey: String,
    private val client: OkHttpClient,
) : TranslatorAPI {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun translate(
        texts: List<String>,
        from: Language,
        to: Language,
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        // Validate API key
        val validationError = validateApiKey(apiKey)
        if (validationError != null) {
            return@withContext texts.map {
                TranslationResult(
                    originalText = it,
                    translatedText = it,
                    error = validationError,
                )
            }
        }

        val batchSize = 10
        val results = mutableListOf<TranslationResult>()

        texts.chunked(batchSize).forEach { batch ->
            try {
                val prompt = buildTranslationPrompt(batch, from, to)
                val response = callGeminiAPI(prompt)
                val translations = parseGeminiResponse(response, batch)
                results.addAll(translations)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini translation failed for batch", e)
                // Fallback: return original text
                batch.forEach { text ->
                    results.add(
                        TranslationResult(
                            originalText = text,
                            translatedText = text,
                            error = e.message,
                        ),
                    )
                }
            }
        }

        return@withContext results
    }

    private fun buildTranslationPrompt(
        texts: List<String>,
        from: Language,
        to: Language,
    ): String {
        return """Translate the following comic dialogue from ${from.displayName} to ${to.displayName}.
Keep the tone, emotional nuance, and personality intact.
Output ONLY the translated text, one per line, in the same order.
Do not add numbers, explanations, or extra formatting.

Original texts:
${texts.joinToString("\n") { "- $it" }}"""
    }

    private suspend fun callGeminiAPI(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBody = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt)),
                ),
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1024,
            ),
        )

        val jsonBody = json.encodeToString(GeminiRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        // Retry logic with exponential backoff
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // Retry on server errors (5xx) and rate limits (429)
                    if (response.code in 500..599 || response.code == 429) {
                        throw Exception("Gemini API error: ${response.code} - ${response.message}")
                    }
                    // Don't retry on client errors (4xx)
                    throw Exception("Gemini API client error: ${response.code} - ${response.message}")
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response from Gemini API")

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)

                return@withContext geminiResponse.candidates.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No text in Gemini response")
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attempt) // Exponential backoff: 1s, 2s, 4s
                    Log.w(TAG, "Gemini API call failed (attempt ${attempt + 1}/$MAX_RETRIES), retrying in ${delayMs}ms", e)
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    Log.e(TAG, "Gemini API call failed after $MAX_RETRIES attempts", e)
                }
            }
        }

        throw lastException ?: Exception("Gemini API call failed")
    }

    private fun parseGeminiResponse(
        response: String,
        originalTexts: List<String>,
    ): List<TranslationResult> {
        // Split response into lines and clean up
        var lines = response.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Remove common formatting artifacts only if they appear at the start of ALL lines
        val allStartWithDash = lines.all { it.startsWith("-") || it.startsWith("•") }
        val allStartWithNumber = lines.all { it.matches(Regex("^\\d+\\.\\s*.*")) }

        lines = when {
            allStartWithDash -> lines.map { it.removePrefix("-").removePrefix("•").trim() }
            allStartWithNumber -> lines.map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
            else -> lines // Keep original if not all lines have the same formatting
        }

        // Match translated lines with original texts
        return originalTexts.mapIndexed { index, originalText ->
            val translatedText = lines.getOrNull(index) ?: originalText
            TranslationResult(
                originalText = originalText,
                translatedText = translatedText,
                confidence = if (lines.getOrNull(index) != null) 0.95f else 0.0f,
                error = if (lines.getOrNull(index) == null) "No translation available" else null,
            )
        }
    }

    override fun getName(): String = "Gemini"

    override suspend fun isAvailable(): Boolean {
        return apiKey.isNotEmpty()
    }

    override fun supportsLanguagePair(from: Language, to: Language): Boolean {
        // Gemini supports all major languages
        return true
    }

    companion object {
        private const val TAG = "GeminiTranslator"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 1 second

        /**
         * Validate Gemini API key format
         * Gemini API keys are typically 39 characters long and alphanumeric
         */
        private fun validateApiKey(apiKey: String): String? {
            return when {
                apiKey.isEmpty() -> "Gemini API key not configured"
                apiKey.length < 20 -> "API key too short (expected ~39 characters)"
                !apiKey.matches(Regex("^[A-Za-z0-9_-]+$")) -> "API key contains invalid characters"
                else -> null // Valid
            }
        }
    }
}

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
private data class Content(
    val parts: List<Part>,
    val role: String = "user",
)

@Serializable
private data class Part(
    val text: String,
)

@Serializable
private data class GenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
)

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate>,
)

@Serializable
private data class Candidate(
    val content: Content,
)
