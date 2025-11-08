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
        if (apiKey.isEmpty()) {
            return@withContext texts.map {
                TranslationResult(
                    originalText = it,
                    translatedText = it,
                    error = "Gemini API key not configured",
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

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Gemini API error: ${response.code} - ${response.message}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Gemini API")

        val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)

        return@withContext geminiResponse.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No text in Gemini response")
    }

    private fun parseGeminiResponse(
        response: String,
        originalTexts: List<String>,
    ): List<TranslationResult> {
        val lines = response.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-") && !it.matches(Regex("^\\d+\\..*")) }

        // Match translated lines with original texts
        return originalTexts.mapIndexed { index, originalText ->
            TranslationResult(
                originalText = originalText,
                translatedText = lines.getOrNull(index) ?: originalText,
                confidence = if (lines.getOrNull(index) != null) 0.95f else 0.0f,
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
