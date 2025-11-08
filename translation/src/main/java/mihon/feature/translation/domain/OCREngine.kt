package mihon.feature.translation.domain

import android.graphics.Bitmap
import android.graphics.RectF
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.OCRResult

/**
 * Interface for extracting text from images using OCR
 */
interface OCREngine {

    /**
     * Extract text from specified regions in an image
     * @param image The source image
     * @param regions List of regions to extract text from
     * @param language The language of the text
     * @return List of OCR results for each region
     */
    suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult>

    /**
     * Extract text from a single region
     */
    suspend fun extractTextFromRegion(
        image: Bitmap,
        region: RectF,
        language: Language,
    ): OCRResult {
        return extractText(image, listOf(region), language).first()
    }

    /**
     * Get the name of this OCR engine
     */
    fun getName(): String

    /**
     * Check if this engine supports the given language
     */
    fun supportsLanguage(language: Language): Boolean
}
