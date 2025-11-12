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
import mihon.feature.translation.data.ocr.PaddleModelDownloadManager
import mihon.feature.translation.domain.BubbleDetector
import mihon.feature.translation.domain.InpaintingEngine
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.TranslationManager
import mihon.feature.translation.domain.TranslationPreferences
import mihon.feature.translation.domain.TranslatorAPI
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.ModelDownloadProgress
import mihon.feature.translation.domain.models.PaddleModelDownloadProgress
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
    private val paddleModelDownloadManager: PaddleModelDownloadManager,
    private val logger: TranslationLogger,
    private val textRenderer: mihon.feature.translation.data.rendering.TextRenderer,
) : TranslationManager {

    private val _preference = MutableStateFlow(getCurrentPreference())
    override val preference: StateFlow<TranslationPreference> = _preference.asStateFlow()

    override val isEnabled: Flow<Boolean> = preferences.translationEnabled().changes()

    override suspend fun translatePage(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): Bitmap {
        logger.separator()
        logger.i(TAG, "Translation requested for chapter $chapterId, page $pageIndex")
        logger.separator()

        try {
            // Check cache first
            if (preferences.cacheEnabled().get()) {
                val cached = cache.get(chapterId, pageIndex)
                if (cached != null) {
                    logger.i(TAG, "✅ Using cached translation for chapter $chapterId, page $pageIndex")
                    return applyTranslationToBitmap(pageImage, cached)
                }
            }

            // Perform translation
            val translationData = performTranslation(pageImage, chapterId, pageIndex)

            if (translationData == null) {
                logger.w(TAG, "Translation returned null - likely no text found on page")
                return pageImage
            }

            // Cache the result
            if (preferences.cacheEnabled().get()) {
                cache.put(chapterId, pageIndex, translationData)
                logger.d(TAG, "Translation cached for future use")
            }

            // Apply translation to bitmap
            return applyTranslationToBitmap(pageImage, translationData)
        } catch (e: Exception) {
            logger.separator()
            logger.e(TAG, "❌ TRANSLATION COMPLETELY FAILED")
            logger.separator()
            logger.e(TAG, "Page: $pageIndex")
            logger.e(TAG, "Error: ${e.message}", e)

            // Provide helpful debugging hints
            when {
                e.message?.contains("Phase 1") == true -> {
                    logger.e(TAG, "💡 HINT: Bubble detection failed")
                    logger.e(TAG, "  - Check if YOLOv10 model is available")
                    logger.e(TAG, "  - Model path: translation/src/main/assets/models/yolov10_bubble_detection.tflite")
                }
                e.message?.contains("Phase 2") == true -> {
                    logger.e(TAG, "💡 HINT: OCR failed")
                    logger.e(TAG, "  - Check if ML Kit or PaddleOCR is properly initialized")
                    logger.e(TAG, "  - Check network connectivity for ML Kit downloads")
                }
                e.message?.contains("Phase 3") == true -> {
                    logger.e(TAG, "💡 HINT: Translation API failed")
                    logger.e(TAG, "  - Check if API key is configured in settings")
                    logger.e(TAG, "  - Check network connectivity")
                    logger.e(TAG, "  - Verify provider settings (Gemini/OpenAI-compatible)")
                }
                e.message?.contains("Phase 4") == true -> {
                    logger.e(TAG, "💡 HINT: Inpainting failed")
                    logger.e(TAG, "  - Check if inpainting engine is available")
                }
                e.message?.contains("Phase 5") == true -> {
                    logger.e(TAG, "💡 HINT: Text rendering failed")
                    logger.e(TAG, "  - This is unexpected, check stack trace")
                }
                else -> {
                    logger.e(TAG, "💡 HINT: Unknown error occurred")
                    logger.e(TAG, "  - Check full stack trace above")
                    logger.e(TAG, "  - Check log file: ${logger.getLogFile().absolutePath}")
                }
            }

            logger.separator()
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

        val startTime = System.currentTimeMillis()
        logger.d(TAG, "===== Starting translation for page $pageIndex =====")
        logger.d(TAG, "Source: ${sourceLanguage.displayName}, Target: ${targetLanguage.displayName}")

        return try {
            // Step 1: Detect speech bubbles
            logger.d(TAG, "📍 PHASE 1/5: Bubble Detection")
            logger.d(TAG, "Detector: ${bubbleDetector.javaClass.simpleName}")
            val detectionResult = try {
                var result: mihon.feature.translation.domain.models.DetectionResult? = null
                val detectionTime = measureTimeMillis {
                    result = bubbleDetector.detectBubbles(pageImage)
                }
                logger.d(TAG, "✅ Bubble detection completed in ${detectionTime}ms")
                logger.d(TAG, "Found ${result!!.bubbles.size} bubbles")
                if (result!!.bubbles.isEmpty()) {
                    logger.w(TAG, "⚠️ No bubbles detected - page may have no text or model issue")
                }
                result!!
            } catch (e: Exception) {
                logger.e(TAG, "❌ PHASE 1 FAILED: Bubble Detection Error", e)
                logger.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 1 failed: Bubble detection error - ${e.message}", e)
            }

            if (detectionResult.bubbles.isEmpty()) {
                logger.w(TAG, "No text bubbles found on page")
                return null
            }

            // Step 2: Extract text from bubbles using OCR
            logger.d(TAG, "📍 PHASE 2/5: OCR (Text Recognition)")
            logger.d(TAG, "OCR Engine: ${ocrEngine.getName()}")
            logger.d(TAG, "Processing ${detectionResult.textRegions.size} regions")
            val ocrResults = try {
                var results: List<mihon.feature.translation.domain.models.OCRResult>? = null
                val ocrTime = measureTimeMillis {
                    results = ocrEngine.extractText(
                        pageImage,
                        detectionResult.textRegions,
                        sourceLanguage,
                    )
                }
                logger.d(TAG, "✅ OCR completed in ${ocrTime}ms")

                val successCount = results!!.count { it.text.isNotEmpty() }
                val errorCount = results!!.count { it.error != null }
                logger.d(TAG, "OCR results: $successCount successful, $errorCount errors")

                // Log individual OCR results for debugging
                results!!.forEachIndexed { index, result ->
                    if (result.error != null) {
                        logger.w(TAG, "  Region $index: ERROR - ${result.error}")
                    } else if (result.text.isEmpty()) {
                        logger.w(TAG, "  Region $index: Empty text")
                    } else {
                        logger.d(TAG, "  Region $index: \"${result.text}\" (confidence: ${result.confidence})")
                    }
                }
                results!!
            } catch (e: Exception) {
                logger.e(TAG, "❌ PHASE 2 FAILED: OCR Error", e)
                logger.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 2 failed: OCR error - ${e.message}", e)
            }

            // Step 3: Translate extracted text
            logger.d(TAG, "📍 PHASE 3/5: Translation API")
            logger.d(TAG, "Translator: ${translator.getName()}")

            // Map texts with their indices to preserve alignment
            val textsWithIndices = ocrResults.mapIndexed { index, ocr ->
                index to ocr.text
            }.filter { it.second.isNotEmpty() }

            logger.d(TAG, "Texts to translate: ${textsWithIndices.size} out of ${ocrResults.size}")

            val translationResultsList = if (textsWithIndices.isNotEmpty()) {
                try {
                    var results: List<TranslationResult>? = null
                    val translationTime = measureTimeMillis {
                        results = translator.translate(textsWithIndices.map { it.second }, sourceLanguage, targetLanguage)
                    }
                    logger.d(TAG, "✅ Translation completed in ${translationTime}ms")

                    val successCount = results!!.count { it.translatedText.isNotEmpty() && it.error == null }
                    val errorCount = results!!.count { it.error != null }
                    logger.d(TAG, "Translation results: $successCount successful, $errorCount errors")

                    // Log translation results for debugging
                    results!!.forEachIndexed { index, result ->
                        if (result.error != null) {
                            logger.w(TAG, "  Text $index: ERROR - ${result.error}")
                        } else {
                            logger.d(TAG, "  Text $index: \"${result.originalText}\" → \"${result.translatedText}\"")
                        }
                    }
                    results!!
                } catch (e: Exception) {
                    logger.e(TAG, "❌ PHASE 3 FAILED: Translation API Error", e)
                    logger.e(TAG, "Error details: ${e.message}")
                    logger.e(TAG, "Check: API key configured? Network available? Provider supported?")
                    throw Exception("Phase 3 failed: Translation API error - ${e.message}", e)
                }
            } else {
                logger.w(TAG, "No texts to translate (all OCR results were empty)")
                emptyList()
            }

            // Create a map of original index to translation result
            val translationMap = textsWithIndices.zip(translationResultsList)
                .associate { (indexedText, result) -> indexedText.first to result }

            // Combine results while maintaining alignment
            val translatedBubbles = detectionResult.bubbles.zip(ocrResults).mapIndexed { index, (bubble, ocr) ->
                val translation = translationMap[index] ?: TranslationResult(
                    originalText = ocr.text,
                    translatedText = ocr.text,
                    confidence = 0f,
                    error = if (ocr.text.isEmpty()) "Empty text, skipped translation" else null,
                )
                TranslatedBubble(
                    bubble = bubble,
                    ocrResult = ocr,
                    translation = translation,
                )
            }

            val processingTime = System.currentTimeMillis() - startTime
            logger.d(TAG, "===== Translation completed successfully in ${processingTime}ms =====")
            logger.d(TAG, "Summary: ${translatedBubbles.size} bubbles processed")

            TranslationData(
                pageIndex = pageIndex,
                chapterId = chapterId,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                bubbles = translatedBubbles,
                processingTimeMs = processingTime,
            )
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.e(TAG, "===== Translation FAILED after ${processingTime}ms =====", e)
            logger.e(TAG, "Error type: ${e.javaClass.simpleName}")
            logger.e(TAG, "Error message: ${e.message}")
            null
        }
    }

    private suspend fun applyTranslationToBitmap(
        original: Bitmap,
        translationData: TranslationData,
    ): Bitmap {
        val startTime = System.currentTimeMillis()
        logger.d(TAG, "===== Applying translation to bitmap =====")

        return try {
            // Step 1: Recreate masks from bubble bounding boxes
            logger.d(TAG, "📍 PHASE 4/5: Inpainting (Text Removal)")
            logger.d(TAG, "Inpainting Engine: ${inpaintingEngine.javaClass.simpleName}")
            logger.d(TAG, "Creating ${translationData.bubbles.size} masks")

            val masks = try {
                recreateMasksFromBubbles(
                    translationData.bubbles.map { it.bubble.boundingBox },
                    original.width,
                    original.height,
                ).also {
                    logger.d(TAG, "✅ Masks created successfully")
                }
            } catch (e: Exception) {
                logger.e(TAG, "❌ PHASE 4 FAILED: Mask creation error", e)
                throw Exception("Phase 4 failed: Mask creation error - ${e.message}", e)
            }

            // Step 2: Inpaint the image to remove original text
            val inpaintedImage = if (masks.isNotEmpty()) {
                try {
                    var result: Bitmap? = null
                    val inpaintTime = measureTimeMillis {
                        result = inpaintingEngine.inpaint(original, masks)
                    }
                    logger.d(TAG, "✅ Inpainting completed in ${inpaintTime}ms")
                    result!!
                } catch (e: Exception) {
                    logger.e(TAG, "❌ PHASE 4 FAILED: Inpainting error", e)
                    logger.e(TAG, "Error details: ${e.message}")
                    throw Exception("Phase 4 failed: Inpainting error - ${e.message}", e)
                }
            } else {
                logger.d(TAG, "⚠️ No masks to inpaint, using original image")
                original.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Clean up masks
            masks.forEach { it.recycle() }

            // Step 3: Draw translated text on the clean image
            logger.d(TAG, "📍 PHASE 5/5: Text Rendering")
            try {
                val canvas = Canvas(inpaintedImage)

                var renderedCount = 0
                var failedCount = 0

                translationData.bubbles.forEach { translatedBubble ->
                    val result = textRenderer.renderBubble(canvas, translatedBubble)

                    when (result) {
                        is mihon.feature.translation.data.rendering.RenderResult.Success -> {
                            renderedCount++
                            logger.d(
                                TAG,
                                "Bubble rendered: ${translatedBubble.translation.translatedText.take(20)}... " +
                                    "(${result.fontSize}sp, ${result.lineCount} lines)",
                            )
                        }
                        is mihon.feature.translation.data.rendering.RenderResult.Failed -> {
                            failedCount++
                            logger.w(TAG, "Bubble rendering failed: ${result.reason}")
                        }
                        is mihon.feature.translation.data.rendering.RenderResult.Empty -> {
                            // Skip empty bubbles silently
                        }
                    }
                }

                logger.d(TAG, "✅ Text rendering completed: $renderedCount texts drawn, $failedCount failed")

                val totalTime = System.currentTimeMillis() - startTime
                logger.d(TAG, "===== Translation applied successfully in ${totalTime}ms =====")

                inpaintedImage
            } catch (e: Exception) {
                logger.e(TAG, "❌ PHASE 5 FAILED: Text rendering error", e)
                logger.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 5 failed: Text rendering error - ${e.message}", e)
            }
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            logger.e(TAG, "===== Applying translation FAILED after ${totalTime}ms =====", e)
            logger.e(TAG, "Error type: ${e.javaClass.simpleName}")
            logger.e(TAG, "Error message: ${e.message}")
            throw e // Re-throw to be handled by caller
        }
    }

    /**
     * Recreate binary masks from bubble bounding boxes
     * Used for inpainting when applying cached translations
     */
    private fun recreateMasksFromBubbles(
        boundingBoxes: List<RectF>,
        width: Int,
        height: Int,
    ): List<Bitmap> {
        return boundingBoxes.map { bounds ->
            Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
                val canvas = Canvas(this)
                val paint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // Draw rounded rectangle for bubble mask
                val radius = 20f
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }
        }
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

    override suspend fun isPaddleDetectionModelDownloaded(): Boolean {
        return paddleModelDownloadManager.isDetectionModelDownloaded()
    }

    override suspend fun isPaddleRecognitionModelDownloaded(language: Language): Boolean {
        return paddleModelDownloadManager.isRecognitionModelDownloaded(language)
    }

    override fun downloadPaddleDetectionModel(): Flow<PaddleModelDownloadProgress> {
        return paddleModelDownloadManager.downloadDetectionModel()
    }

    override fun downloadPaddleRecognitionModel(language: Language): Flow<PaddleModelDownloadProgress> {
        return paddleModelDownloadManager.downloadRecognitionModel(language)
    }

    override fun downloadAllPaddleModels(language: Language): Flow<PaddleModelDownloadProgress> {
        return paddleModelDownloadManager.downloadAllModels(language)
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
