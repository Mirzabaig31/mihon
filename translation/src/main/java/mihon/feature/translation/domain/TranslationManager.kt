package mihon.feature.translation.domain

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import mihon.feature.translation.data.ocr.ModelDownloadProgress
import mihon.feature.translation.data.ocr.PaddleModelDownloadProgress
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.TranslationData
import mihon.feature.translation.domain.models.TranslationPreference

/**
 * Main interface for managing comic page translation
 * This is the primary entry point for the translation feature
 */
interface TranslationManager {

    /**
     * Current translation preference state
     */
    val preference: StateFlow<TranslationPreference>

    /**
     * Whether translation is currently enabled
     */
    val isEnabled: Flow<Boolean>

    /**
     * Translate a comic page image
     * @param pageImage The original page bitmap
     * @param chapterId The chapter ID for caching
     * @param pageIndex The page index within the chapter
     * @return Translated page bitmap with text replaced
     */
    suspend fun translatePage(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): Bitmap

    /**
     * Get translation data without applying it to the image
     * Useful for showing original/translated text side-by-side
     */
    suspend fun getTranslationData(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): TranslationData?

    /**
     * Check if a page has cached translation data
     */
    suspend fun hasCachedTranslation(
        chapterId: Long,
        pageIndex: Int,
    ): Boolean

    /**
     * Clear cached translations for a chapter
     */
    suspend fun clearCache(chapterId: Long)

    /**
     * Clear all cached translations
     */
    suspend fun clearAllCache()

    /**
     * Update translation preferences
     */
    suspend fun updatePreference(preference: TranslationPreference)

    /**
     * Set whether translation is enabled
     */
    suspend fun setEnabled(enabled: Boolean)

    /**
     * Set source and target languages
     */
    suspend fun setLanguages(source: Language, target: Language)

    /**
     * Check if ML Kit OCR model for a language is downloaded
     */
    suspend fun isModelDownloaded(language: Language): Boolean

    /**
     * Download ML Kit OCR model for a specific language
     * Returns a Flow that emits download progress
     */
    fun downloadModel(language: Language): Flow<ModelDownloadProgress>

    /**
     * Download ML Kit OCR models for multiple languages
     * Returns a Flow that emits download progress for all models
     */
    fun downloadModels(languages: List<Language>): Flow<ModelDownloadProgress>

    /**
     * Check if PaddleOCR detection model is downloaded
     */
    suspend fun isPaddleDetectionModelDownloaded(): Boolean

    /**
     * Check if PaddleOCR recognition model for a language is downloaded
     */
    suspend fun isPaddleRecognitionModelDownloaded(language: Language): Boolean

    /**
     * Download PaddleOCR detection model
     * Returns a Flow that emits download progress
     */
    fun downloadPaddleDetectionModel(): Flow<PaddleModelDownloadProgress>

    /**
     * Download PaddleOCR recognition model for a specific language
     * Returns a Flow that emits download progress
     */
    fun downloadPaddleRecognitionModel(language: Language): Flow<PaddleModelDownloadProgress>

    /**
     * Download all PaddleOCR models for a language (detection + recognition)
     * Returns a Flow that emits download progress
     */
    fun downloadAllPaddleModels(language: Language): Flow<PaddleModelDownloadProgress>
}
