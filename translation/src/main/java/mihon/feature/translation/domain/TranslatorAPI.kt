package mihon.feature.translation.domain

import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.TranslationResult

/**
 * Interface for translating text between languages
 */
interface TranslatorAPI {

    /**
     * Translate multiple texts from one language to another
     * @param texts List of texts to translate
     * @param from Source language
     * @param to Target language
     * @return List of translation results
     */
    suspend fun translate(
        texts: List<String>,
        from: Language,
        to: Language,
    ): List<TranslationResult>

    /**
     * Translate a single text
     */
    suspend fun translateSingle(
        text: String,
        from: Language,
        to: Language,
    ): TranslationResult {
        return translate(listOf(text), from, to).first()
    }

    /**
     * Get the name of this translator
     */
    fun getName(): String

    /**
     * Check if this translator is available (e.g., has valid API key)
     */
    suspend fun isAvailable(): Boolean

    /**
     * Check if this translator supports the given language pair
     */
    fun supportsLanguagePair(from: Language, to: Language): Boolean
}
