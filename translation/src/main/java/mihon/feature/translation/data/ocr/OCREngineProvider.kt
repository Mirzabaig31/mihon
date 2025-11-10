package mihon.feature.translation.data.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.TranslationPreferences
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.OCRResult

/**
 * Provides the appropriate OCR engine based on user preferences
 * Supports switching between ML Kit and PaddleOCR
 * Implements OCREngine interface and delegates to the selected engine
 */
class OCREngineProvider(
    private val mlKitEngine: MLKitOCREngine,
    private val paddleEngine: PaddleOCREngine,
    private val preferences: TranslationPreferences,
) : OCREngine {

    /**
     * Get the currently selected OCR engine based on user preference
     */
    private fun getEngine(): OCREngine {
        val provider = preferences.ocrProvider().get()
        return when (provider) {
            TranslationPreferences.OCR_PADDLE -> paddleEngine
            TranslationPreferences.OCR_ML_KIT -> mlKitEngine
            else -> {
                // Auto mode: use ML Kit as default since it's easier to set up
                mlKitEngine
            }
        }
    }

    override suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult> {
        return getEngine().extractText(image, regions, language)
    }

    override fun getName(): String {
        return getEngine().getName()
    }

    override fun supportsLanguage(language: Language): Boolean {
        return getEngine().supportsLanguage(language)
    }

    /**
     * Get ML Kit engine specifically
     */
    fun getMLKitEngine(): MLKitOCREngine = mlKitEngine

    /**
     * Get PaddleOCR engine specifically
     */
    fun getPaddleEngine(): PaddleOCREngine = paddleEngine

    /**
     * Clean up resources for all engines
     */
    fun close() {
        mlKitEngine.close()
        paddleEngine.close()
    }
}
