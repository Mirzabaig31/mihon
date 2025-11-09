package mihon.feature.translation.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import mihon.feature.translation.data.ocr.ModelDownloadManager
import mihon.feature.translation.data.ocr.ModelDownloadProgress
import mihon.feature.translation.domain.BubbleDetector
import mihon.feature.translation.domain.InpaintingEngine
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.TranslationManager
import mihon.feature.translation.domain.TranslationPreferences
import mihon.feature.translation.domain.TranslatorAPI
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.TranslatedBubble
import mihon.feature.translation.domain.models.TranslationData
import mihon.feature.translation.domain.models.TranslationPreference
import mihon.feature.translation.domain.models.TranslationResult
import kotlin.system.measureTimeMillis

/**
 * Main implementation of the translation manager
 * Coordinates bubble detection, OCR, translation, and inpainting
 */
class TranslationManagerImpl(
    private val bubbleDetector: BubbleDetector,
    private val ocrEngine: OCREngine,
    private val translator: TranslatorAPI,
    private val inpaintingEngine: InpaintingEngine,
    private val preferences: TranslationPreferences,
    private val cache: TranslationCache,
    private val modelDownloadManager: ModelDownloadManager,
) : TranslationManager {

    private val _preference = MutableStateFlow(getCurrentPreference())
    override val preference: StateFlow<TranslationPreference> = _preference.asStateFlow()

    override val isEnabled: Flow<Boolean> = preferences.translationEnabled().changes()

    override suspend fun translatePage(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): Bitmap {
        try {
            // Check cache first
            if (preferences.cacheEnabled().get()) {
                val cached = cache.get(chapterId, pageIndex)
                if (cached != null) {
                    Log.d(TAG, "Using cached translation for chapter $chapterId, page $pageIndex")
                    return applyTranslationToBitmap(pageImage, cached)
                }
            }

            // Perform translation
            val translationData = performTranslation(pageImage, chapterId, pageIndex)

            // Cache the result
            if (preferences.cacheEnabled().get() && translationData != null) {
                cache.put(chapterId, pageIndex, translationData)
            }

            // Apply translation to bitmap
            return if (translationData != null) {
                applyTranslationToBitmap(pageImage, translationData)
            } else {
                pageImage
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed for page $pageIndex", e)
            return pageImage
        }
    }

    override suspend fun getTranslationData(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): TranslationData? {
        // Check cache first
        if (preferences.cacheEnabled().get()) {
            val cached = cache.get(chapterId, pageIndex)
            if (cached != null) {
                return cached
            }
        }

        // Perform translation
        val translationData = performTranslation(pageImage, chapterId, pageIndex)

        // Cache the result
        if (preferences.cacheEnabled().get() && translationData != null) {
            cache.put(chapterId, pageIndex, translationData)
        }

        return translationData
    }

    private suspend fun performTranslation(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): TranslationData? {
        val sourceLanguage = Language.fromCode(preferences.sourceLanguage().get())
            ?: Language.JAPANESE
        val targetLanguage = Language.fromCode(preferences.targetLanguage().get())
            ?: Language.ENGLISH

        return try {
            val startTime = System.currentTimeMillis()

            // Step 1: Detect speech bubbles
            Log.d(TAG, "Step 1: Detecting bubbles...")
            val detectionResult = bubbleDetector.detectBubbles(pageImage)
            Log.d(TAG, "Detected ${detectionResult.bubbles.size} bubbles")

            // Step 2: Extract text from bubbles using OCR
            Log.d(TAG, "Step 2: Performing OCR...")
            val ocrResults = ocrEngine.extractText(
                pageImage,
                detectionResult.textRegions,
                sourceLanguage,
            )
            Log.d(TAG, "OCR completed for ${ocrResults.size} regions")

            // Step 3: Translate extracted text
            Log.d(TAG, "Step 3: Translating text...")
            // Map texts with their indices to preserve alignment
            val textsWithIndices = ocrResults.mapIndexed { index, ocr ->
                index to ocr.text
            }.filter { it.second.isNotEmpty() }

            val translationResultsList = if (textsWithIndices.isNotEmpty()) {
                translator.translate(textsWithIndices.map { it.second }, sourceLanguage, targetLanguage)
            } else {
                emptyList()
            }

            // Create a map of original index to translation result
            val translationMap = textsWithIndices.zip(translationResultsList)
                .associate { (indexedText, result) -> indexedText.first to result }
            Log.d(TAG, "Translation completed for ${translationResultsList.size} texts")

            // Combine results while maintaining alignment
            val translatedBubbles = detectionResult.bubbles.zip(ocrResults).mapIndexed { index, (bubble, ocr) ->
                val translation = translationMap[index] ?: TranslationResult(
                    originalText = ocr.text,
                    translatedText = ocr.text,
                    confidence = 0f,
                    error = "Empty text, skipped translation",
                )
                TranslatedBubble(
                    bubble = bubble,
                    ocrResult = ocr,
                    translation = translation,
                )
            }

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Total processing time: ${processingTime}ms")

            TranslationData(
                pageIndex = pageIndex,
                chapterId = chapterId,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                bubbles = translatedBubbles,
                processingTimeMs = processingTime,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Translation processing failed", e)
            null
        }
    }

    private fun applyTranslationToBitmap(
        original: Bitmap,
        translationData: TranslationData,
    ): Bitmap {
        // Create a mutable copy
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // For Phase 1, we'll just draw translated text on top
        // In later phases, we'll use inpainting to remove original text first
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }

        val backgroundPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = 200
        }

        translationData.bubbles.forEach { translatedBubble ->
            val bounds = translatedBubble.bubble.boundingBox
            val translatedText = translatedBubble.translation.translatedText

            // Draw white background
            canvas.drawRect(bounds, backgroundPaint)

            // Draw translated text (simple for now)
            val x = bounds.left + 10f
            val y = bounds.top + 30f
            canvas.drawText(translatedText, x, y, paint)
        }

        return result
    }

    override suspend fun hasCachedTranslation(chapterId: Long, pageIndex: Int): Boolean {
        return cache.has(chapterId, pageIndex)
    }

    override suspend fun clearCache(chapterId: Long) {
        cache.clearChapter(chapterId)
    }

    override suspend fun clearAllCache() {
        cache.clearAll()
    }

    override suspend fun updatePreference(preference: TranslationPreference) {
        preferences.translationEnabled().set(preference.enabled)
        preferences.sourceLanguage().set(preference.sourceLanguage.code)
        preferences.targetLanguage().set(preference.targetLanguage.code)
        preferences.translatorProvider().set(preference.translatorProvider.name.lowercase())
        preferences.ocrProvider().set(preference.ocrProvider.name.lowercase())
        preferences.showOriginalText().set(preference.showOriginalText)
        preferences.autoTranslate().set(preference.autoTranslate)

        _preference.value = preference
    }

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.translationEnabled().set(enabled)
        _preference.value = _preference.value.copy(enabled = enabled)
    }

    override suspend fun setLanguages(source: Language, target: Language) {
        preferences.sourceLanguage().set(source.code)
        preferences.targetLanguage().set(target.code)
        _preference.value = _preference.value.copy(
            sourceLanguage = source,
            targetLanguage = target,
        )
    }

    override suspend fun isModelDownloaded(language: Language): Boolean {
        return modelDownloadManager.isModelDownloaded(language)
    }

    override fun downloadModel(language: Language): Flow<ModelDownloadProgress> {
        return modelDownloadManager.downloadModel(language)
    }

    override fun downloadModels(languages: List<Language>): Flow<ModelDownloadProgress> {
        return modelDownloadManager.downloadModels(languages)
    }

    private fun getCurrentPreference(): TranslationPreference {
        return TranslationPreference(
            enabled = preferences.translationEnabled().get(),
            sourceLanguage = Language.fromCode(preferences.sourceLanguage().get())
                ?: Language.JAPANESE,
            targetLanguage = Language.fromCode(preferences.targetLanguage().get())
                ?: Language.ENGLISH,
            showOriginalText = preferences.showOriginalText().get(),
            autoTranslate = preferences.autoTranslate().get(),
        )
    }

    companion object {
        private const val TAG = "TranslationManager"
    }
}
