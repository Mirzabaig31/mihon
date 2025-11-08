package mihon.feature.translation.data.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.OCRResult

/**
 * Stub implementation for OCR
 * This will be replaced with ML Kit in Phase 2
 */
class StubOCREngine : OCREngine {

    override suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult> {
        // For Phase 1, return placeholder text
        // This allows testing the translation API without OCR
        return regions.mapIndexed { index, region ->
            OCRResult(
                text = "Sample text ${index + 1} in ${language.displayName}",
                confidence = 0.0f,
                boundingBox = region,
                error = "Stub OCR - real OCR not yet implemented",
            )
        }
    }

    override fun getName(): String = "Stub OCR (Phase 1)"

    override fun supportsLanguage(language: Language): Boolean = true
}
