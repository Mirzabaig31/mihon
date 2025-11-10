package mihon.feature.translation.data.ocr

import android.content.Context
import android.util.Log
import mihon.feature.translation.domain.models.Language
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Dictionary loader for PaddleOCR character mapping
 *
 * Maps character indices from CTC decoder to actual characters.
 * Dictionaries are language-specific:
 * - Japanese: ~6000 characters (Kanji + Hiragana + Katakana)
 * - Chinese: ~6000 characters (Simplified/Traditional)
 * - Korean: ~3000 characters (Hangul)
 * - English: ~100 characters (Alphanumeric + punctuation)
 */
class PaddleOCRDictionary(private val context: Context) {

    private val cache = mutableMapOf<Language, List<String>>()

    /**
     * Load dictionary for specified language
     *
     * Dictionaries are stored in assets/ocr_dictionaries/
     * Format: One character per line, index = line number
     *
     * @param language Target language
     * @return List of characters (index → character)
     */
    fun loadDictionary(language: Language): List<String> {
        // Check cache first
        cache[language]?.let { return it }

        val dictionaryFile = getDictionaryFilename(language)

        try {
            val dictionary = context.assets.open("ocr_dictionaries/$dictionaryFile").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    // Add blank token at index 0
                    listOf("<blank>") + lines.toList()
                }
            }

            Log.d(TAG, "Loaded dictionary for $language: ${dictionary.size} characters")
            cache[language] = dictionary
            return dictionary
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary for $language", e)

            // Return fallback dictionary
            return getFallbackDictionary(language).also {
                cache[language] = it
            }
        }
    }

    /**
     * Get dictionary filename for language
     */
    private fun getDictionaryFilename(language: Language): String {
        return when (language) {
            Language.JAPANESE -> "ppocr_keys_v1_japan.txt"
            Language.CHINESE_SIMPLIFIED -> "ppocr_keys_v1_ch.txt"
            Language.CHINESE_TRADITIONAL -> "ppocr_keys_v1_chinese_cht.txt"
            Language.KOREAN -> "ppocr_keys_v1_korean.txt"
            Language.ENGLISH -> "ppocr_keys_v1_en.txt"
            else -> "ppocr_keys_v1_en.txt" // Default to English
        }
    }

    /**
     * Get fallback dictionary if file loading fails
     *
     * Contains basic ASCII characters for minimal functionality
     */
    private fun getFallbackDictionary(language: Language): List<String> {
        val basic = listOf("<blank>") + ('0'..'9').map { it.toString() } +
            ('a'..'z').map { it.toString() } +
            ('A'..'Z').map { it.toString() } +
            listOf(" ", ".", ",", "!", "?", "'", "\"", "-", "_")

        Log.w(TAG, "Using fallback dictionary with ${basic.size} characters")
        return basic
    }

    /**
     * Check if dictionary is available for language
     */
    fun isDictionaryAvailable(language: Language): Boolean {
        val dictionaryFile = getDictionaryFilename(language)
        return try {
            context.assets.open("ocr_dictionaries/$dictionaryFile").use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear dictionary cache
     */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "Dictionary cache cleared")
    }

    companion object {
        private const val TAG = "PaddleOCRDictionary"

        /**
         * Dictionary download URLs
         * Dictionaries should be placed in: translation/src/main/assets/ocr_dictionaries/
         */
        val DICTIONARY_URLS = mapOf(
            Language.JAPANESE to "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/japan_dict.txt",
            Language.CHINESE_SIMPLIFIED to "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/ppocr_keys_v1.txt",
            Language.CHINESE_TRADITIONAL to "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/chinese_cht_dict.txt",
            Language.KOREAN to "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/korean_dict.txt",
            Language.ENGLISH to "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict/en_dict.txt",
        )
    }
}

/**
 * Simple character set for fallback/testing
 */
object BasicCharacterSet {
    // Numbers
    val DIGITS = ('0'..'9').map { it.toString() }

    // English lowercase
    val LOWERCASE = ('a'..'z').map { it.toString() }

    // English uppercase
    val UPPERCASE = ('A'..'Z').map { it.toString() }

    // Common punctuation
    val PUNCTUATION = listOf(" ", ".", ",", "!", "?", "'", "\"", "-", "_", "(", ")", "[", "]", "{", "}")

    // Japanese Hiragana (basic)
    val HIRAGANA = listOf(
        "あ", "い", "う", "え", "お",
        "か", "き", "く", "け", "こ",
        "さ", "し", "す", "せ", "そ",
        "た", "ち", "つ", "て", "と",
        "な", "に", "ぬ", "ね", "の",
        "は", "ひ", "ふ", "へ", "ほ",
        "ま", "み", "む", "め", "も",
        "や", "ゆ", "よ",
        "ら", "り", "る", "れ", "ろ",
        "わ", "を", "ん",
    )

    // Japanese Katakana (basic)
    val KATAKANA = listOf(
        "ア", "イ", "ウ", "エ", "オ",
        "カ", "キ", "ク", "ケ", "コ",
        "サ", "シ", "ス", "セ", "ソ",
        "タ", "チ", "ツ", "テ", "ト",
        "ナ", "ニ", "ヌ", "ネ", "ノ",
        "ハ", "ヒ", "フ", "ヘ", "ホ",
        "マ", "ミ", "ム", "メ", "モ",
        "ヤ", "ユ", "ヨ",
        "ラ", "リ", "ル", "レ", "ロ",
        "ワ", "ヲ", "ン",
    )

    /**
     * Get combined character set for Japanese
     */
    fun getJapaneseBasic(): List<String> {
        return listOf("<blank>") + DIGITS + LOWERCASE + UPPERCASE + PUNCTUATION + HIRAGANA + KATAKANA
    }

    /**
     * Get combined character set for English
     */
    fun getEnglishBasic(): List<String> {
        return listOf("<blank>") + DIGITS + LOWERCASE + UPPERCASE + PUNCTUATION
    }
}
