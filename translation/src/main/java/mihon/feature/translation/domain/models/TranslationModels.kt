package mihon.feature.translation.domain.models

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Represents a detected speech bubble in a comic page
 */
data class SpeechBubble(
    val boundingBox: RectF,
    val confidence: Float,
    val type: BubbleType = BubbleType.SPEECH,
    val textStyle: TextStyle = TextStyle.HORIZONTAL,
)

enum class BubbleType {
    SPEECH,
    THOUGHT,
    NARRATION,
    SOUND_EFFECT,
}

enum class TextStyle {
    HORIZONTAL,
    VERTICAL,
    MIXED,
}

/**
 * Result of bubble detection on a page
 */
data class DetectionResult(
    val bubbles: List<SpeechBubble>,
    val textRegions: List<RectF>,
    val masks: List<Bitmap>,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Result of OCR text extraction
 */
data class OCRResult(
    val text: String,
    val confidence: Float,
    val boundingBox: RectF,
    val textBlocks: List<TextBlock> = emptyList(),
    val error: String? = null,
)

data class TextBlock(
    val text: String,
    val boundingBox: RectF,
    val confidence: Float?,
)

/**
 * Result of translation
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val confidence: Float = 1.0f,
    val error: String? = null,
)

/**
 * Language configuration
 */
enum class Language(val code: String, val displayName: String) {
    JAPANESE("ja", "Japanese"),
    CHINESE_SIMPLIFIED("zh-CN", "Chinese (Simplified)"),
    CHINESE_TRADITIONAL("zh-TW", "Chinese (Traditional)"),
    KOREAN("ko", "Korean"),
    ENGLISH("en", "English"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    PORTUGUESE("pt", "Portuguese"),
    RUSSIAN("ru", "Russian"),
    ITALIAN("it", "Italian"),
    ;

    fun toGoogleCode(): String = code

    companion object {
        fun fromCode(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}

/**
 * Complete translation data for a page
 */
data class TranslationData(
    val pageIndex: Int,
    val chapterId: Long,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val bubbles: List<TranslatedBubble>,
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class TranslatedBubble(
    val bubble: SpeechBubble,
    val ocrResult: OCRResult,
    val translation: TranslationResult,
)

/**
 * Translation preferences
 */
data class TranslationPreference(
    val enabled: Boolean = false,
    val sourceLanguage: Language = Language.JAPANESE,
    val targetLanguage: Language = Language.ENGLISH,
    val translatorProvider: TranslatorProvider = TranslatorProvider.GEMINI,
    val ocrProvider: OCRProvider = OCRProvider.ML_KIT,
    val bubbleDetector: BubbleDetector = BubbleDetector.YOLOV10,
    val inpaintingEngine: InpaintingEngine = InpaintingEngine.LAMA,
    val showOriginalText: Boolean = false,
    val autoTranslate: Boolean = false,
)

enum class TranslatorProvider {
    GEMINI,
    GOOGLE_CLOUD,
    OPENAI_COMPAT,
    ML_KIT,
    AUTO,
}

enum class OCRProvider {
    ML_KIT,
    PADDLE,
    CLOUD,
    AUTO,
}

enum class BubbleDetector {
    YOLOV10,
    YOLOV8M,
    YOLOV8N,
}

enum class InpaintingEngine {
    LAMA,
    DIFFUSION,
    SIMPLE,
}
