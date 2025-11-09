package mihon.feature.translation.data.ocr

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import mihon.feature.translation.domain.models.Language

/**
 * Manages ML Kit TextRecognizer instances for different languages
 * Caches recognizers to avoid repeated initialization
 */
class TextRecognizerProvider {

    private var japaneseRecognizer: TextRecognizer? = null
    private var chineseRecognizer: TextRecognizer? = null
    private var koreanRecognizer: TextRecognizer? = null
    private var latinRecognizer: TextRecognizer? = null

    /**
     * Get appropriate text recognizer for the given language
     * Recognizers are cached and reused
     */
    fun getRecognizer(language: Language): TextRecognizer {
        return when (language) {
            Language.JAPANESE -> {
                japaneseRecognizer ?: createJapaneseRecognizer().also {
                    japaneseRecognizer = it
                }
            }
            Language.CHINESE_SIMPLIFIED,
            Language.CHINESE_TRADITIONAL,
            -> {
                chineseRecognizer ?: createChineseRecognizer().also {
                    chineseRecognizer = it
                }
            }
            Language.KOREAN -> {
                koreanRecognizer ?: createKoreanRecognizer().also {
                    koreanRecognizer = it
                }
            }
            else -> {
                latinRecognizer ?: createLatinRecognizer().also {
                    latinRecognizer = it
                }
            }
        }
    }

    private fun createJapaneseRecognizer(): TextRecognizer {
        val options = JapaneseTextRecognizerOptions.Builder().build()
        return TextRecognition.getClient(options)
    }

    private fun createChineseRecognizer(): TextRecognizer {
        val options = ChineseTextRecognizerOptions.Builder().build()
        return TextRecognition.getClient(options)
    }

    private fun createKoreanRecognizer(): TextRecognizer {
        val options = KoreanTextRecognizerOptions.Builder().build()
        return TextRecognition.getClient(options)
    }

    private fun createLatinRecognizer(): TextRecognizer {
        val options = TextRecognizerOptions.Builder().build()
        return TextRecognition.getClient(options)
    }

    /**
     * Close all recognizers and free resources
     * Call this when OCR engine is no longer needed
     */
    fun close() {
        japaneseRecognizer?.close()
        chineseRecognizer?.close()
        koreanRecognizer?.close()
        latinRecognizer?.close()

        japaneseRecognizer = null
        chineseRecognizer = null
        koreanRecognizer = null
        latinRecognizer = null
    }
}
