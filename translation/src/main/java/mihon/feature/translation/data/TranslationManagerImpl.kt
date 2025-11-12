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
) : TranslationManager {

    private val _preference = MutableStateFlow(getCurrentPreference())
    override val preference: StateFlow<TranslationPreference> = _preference.asStateFlow()

    override val isEnabled: Flow<Boolean> = preferences.translationEnabled().changes()

    override suspend fun translatePage(
        pageImage: Bitmap,
        chapterId: Long,
        pageIndex: Int,
    ): Bitmap {
        Log.i(TAG, "========================================")
        Log.i(TAG, "Translation requested for chapter $chapterId, page $pageIndex")
        Log.i(TAG, "========================================")

        try {
            // Check cache first
            if (preferences.cacheEnabled().get()) {
                val cached = cache.get(chapterId, pageIndex)
                if (cached != null) {
                    Log.i(TAG, "✅ Using cached translation for chapter $chapterId, page $pageIndex")
                    return applyTranslationToBitmap(pageImage, cached)
                }
            }

            // Perform translation
            val translationData = performTranslation(pageImage, chapterId, pageIndex)

            if (translationData == null) {
                Log.w(TAG, "Translation returned null - likely no text found on page")
                return pageImage
            }

            // Cache the result
            if (preferences.cacheEnabled().get()) {
                cache.put(chapterId, pageIndex, translationData)
                Log.d(TAG, "Translation cached for future use")
            }

            // Apply translation to bitmap
            return applyTranslationToBitmap(pageImage, translationData)
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ TRANSLATION COMPLETELY FAILED")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Page: $pageIndex")
            Log.e(TAG, "Error: ${e.message}", e)

            // Provide helpful debugging hints
            when {
                e.message?.contains("Phase 1") == true -> {
                    Log.e(TAG, "💡 HINT: Bubble detection failed")
                    Log.e(TAG, "  - Check if YOLOv10 model is available")
                    Log.e(TAG, "  - Model path: translation/src/main/assets/models/yolov10_bubble_detection.tflite")
                }
                e.message?.contains("Phase 2") == true -> {
                    Log.e(TAG, "💡 HINT: OCR failed")
                    Log.e(TAG, "  - Check if ML Kit or PaddleOCR is properly initialized")
                    Log.e(TAG, "  - Check network connectivity for ML Kit downloads")
                }
                e.message?.contains("Phase 3") == true -> {
                    Log.e(TAG, "💡 HINT: Translation API failed")
                    Log.e(TAG, "  - Check if API key is configured in settings")
                    Log.e(TAG, "  - Check network connectivity")
                    Log.e(TAG, "  - Verify provider settings (Gemini/OpenAI-compatible)")
                }
                e.message?.contains("Phase 4") == true -> {
                    Log.e(TAG, "💡 HINT: Inpainting failed")
                    Log.e(TAG, "  - Check if inpainting engine is available")
                }
                e.message?.contains("Phase 5") == true -> {
                    Log.e(TAG, "💡 HINT: Text rendering failed")
                    Log.e(TAG, "  - This is unexpected, check stack trace")
                }
                else -> {
                    Log.e(TAG, "💡 HINT: Unknown error occurred")
                    Log.e(TAG, "  - Check full stack trace above")
                    Log.e(TAG, "  - Check logcat for detailed logs")
                }
            }

            Log.e(TAG, "========================================")
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
        Log.d(TAG, "===== Starting translation for page $pageIndex =====")
        Log.d(TAG, "Source: ${sourceLanguage.displayName}, Target: ${targetLanguage.displayName}")

        return try {
            // Step 1: Detect speech bubbles
            Log.d(TAG, "📍 PHASE 1/5: Bubble Detection")
            Log.d(TAG, "Detector: ${bubbleDetector.javaClass.simpleName}")
            val detectionResult = try {
                val detectionTime = measureTimeMillis {
                    bubbleDetector.detectBubbles(pageImage)
                }.also { time ->
                    Log.d(TAG, "✅ Bubble detection completed in ${time}ms")
                }
                bubbleDetector.detectBubbles(pageImage).also { result ->
                    Log.d(TAG, "Found ${result.bubbles.size} bubbles")
                    if (result.bubbles.isEmpty()) {
                        Log.w(TAG, "⚠️ No bubbles detected - page may have no text or model issue")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ PHASE 1 FAILED: Bubble Detection Error", e)
                Log.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 1 failed: Bubble detection error - ${e.message}", e)
            }

            if (detectionResult.bubbles.isEmpty()) {
                Log.w(TAG, "No text bubbles found on page")
                return null
            }

            // Step 2: Extract text from bubbles using OCR
            Log.d(TAG, "📍 PHASE 2/5: OCR (Text Recognition)")
            Log.d(TAG, "OCR Engine: ${ocrEngine.getName()}")
            Log.d(TAG, "Processing ${detectionResult.textRegions.size} regions")
            val ocrResults = try {
                val ocrTime = measureTimeMillis {
                    ocrEngine.extractText(
                        pageImage,
                        detectionResult.textRegions,
                        sourceLanguage,
                    )
                }.also { time ->
                    Log.d(TAG, "✅ OCR completed in ${time}ms")
                }
                ocrEngine.extractText(
                    pageImage,
                    detectionResult.textRegions,
                    sourceLanguage,
                ).also { results ->
                    val successCount = results.count { it.text.isNotEmpty() }
                    val errorCount = results.count { it.error != null }
                    Log.d(TAG, "OCR results: $successCount successful, $errorCount errors")

                    // Log individual OCR results for debugging
                    results.forEachIndexed { index, result ->
                        if (result.error != null) {
                            Log.w(TAG, "  Region $index: ERROR - ${result.error}")
                        } else if (result.text.isEmpty()) {
                            Log.w(TAG, "  Region $index: Empty text")
                        } else {
                            Log.d(TAG, "  Region $index: \"${result.text}\" (confidence: ${result.confidence})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ PHASE 2 FAILED: OCR Error", e)
                Log.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 2 failed: OCR error - ${e.message}", e)
            }

            // Step 3: Translate extracted text
            Log.d(TAG, "📍 PHASE 3/5: Translation API")
            Log.d(TAG, "Translator: ${translator.getName()}")

            // Map texts with their indices to preserve alignment
            val textsWithIndices = ocrResults.mapIndexed { index, ocr ->
                index to ocr.text
            }.filter { it.second.isNotEmpty() }

            Log.d(TAG, "Texts to translate: ${textsWithIndices.size} out of ${ocrResults.size}")

            val translationResultsList = if (textsWithIndices.isNotEmpty()) {
                try {
                    val translationTime = measureTimeMillis {
                        translator.translate(textsWithIndices.map { it.second }, sourceLanguage, targetLanguage)
                    }.also { time ->
                        Log.d(TAG, "✅ Translation completed in ${time}ms")
                    }
                    translator.translate(textsWithIndices.map { it.second }, sourceLanguage, targetLanguage).also { results ->
                        val successCount = results.count { it.translatedText.isNotEmpty() && it.error == null }
                        val errorCount = results.count { it.error != null }
                        Log.d(TAG, "Translation results: $successCount successful, $errorCount errors")

                        // Log translation results for debugging
                        results.forEachIndexed { index, result ->
                            if (result.error != null) {
                                Log.w(TAG, "  Text $index: ERROR - ${result.error}")
                            } else {
                                Log.d(TAG, "  Text $index: \"${result.originalText}\" → \"${result.translatedText}\"")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ PHASE 3 FAILED: Translation API Error", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    Log.e(TAG, "Check: API key configured? Network available? Provider supported?")
                    throw Exception("Phase 3 failed: Translation API error - ${e.message}", e)
                }
            } else {
                Log.w(TAG, "No texts to translate (all OCR results were empty)")
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
            Log.d(TAG, "===== Translation completed successfully in ${processingTime}ms =====")
            Log.d(TAG, "Summary: ${translatedBubbles.size} bubbles processed")

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
            Log.e(TAG, "===== Translation FAILED after ${processingTime}ms =====", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }

    private suspend fun applyTranslationToBitmap(
        original: Bitmap,
        translationData: TranslationData,
    ): Bitmap {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "===== Applying translation to bitmap =====")

        return try {
            // Step 1: Recreate masks from bubble bounding boxes
            Log.d(TAG, "📍 PHASE 4/5: Inpainting (Text Removal)")
            Log.d(TAG, "Inpainting Engine: ${inpaintingEngine.javaClass.simpleName}")
            Log.d(TAG, "Creating ${translationData.bubbles.size} masks")

            val masks = try {
                recreateMasksFromBubbles(
                    translationData.bubbles.map { it.bubble.boundingBox },
                    original.width,
                    original.height,
                ).also {
                    Log.d(TAG, "✅ Masks created successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ PHASE 4 FAILED: Mask creation error", e)
                throw Exception("Phase 4 failed: Mask creation error - ${e.message}", e)
            }

            // Step 2: Inpaint the image to remove original text
            val inpaintedImage = if (masks.isNotEmpty()) {
                try {
                    var result: Bitmap? = null
                    val inpaintTime = measureTimeMillis {
                        result = inpaintingEngine.inpaint(original, masks)
                    }
                    Log.d(TAG, "✅ Inpainting completed in ${inpaintTime}ms")
                    result!!
                } catch (e: Exception) {
                    Log.e(TAG, "❌ PHASE 4 FAILED: Inpainting error", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    throw Exception("Phase 4 failed: Inpainting error - ${e.message}", e)
                }
            } else {
                Log.d(TAG, "⚠️ No masks to inpaint, using original image")
                original.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Clean up masks
            masks.forEach { it.recycle() }

            // Step 3: Draw translated text on the clean image
            Log.d(TAG, "📍 PHASE 5/5: Text Rendering")
            try {
                val canvas = Canvas(inpaintedImage)

                val textPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }

                val backgroundPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    alpha = 230 // Slightly transparent for better blending
                }

                var renderedCount = 0
                translationData.bubbles.forEach { translatedBubble ->
                    val bounds = translatedBubble.bubble.boundingBox
                    val translatedText = translatedBubble.translation.translatedText

                    if (translatedText.isNotEmpty()) {
                        // Draw semi-transparent white background for text
                        canvas.drawRect(bounds, backgroundPaint)

                        // Draw translated text centered in bubble
                        val centerX = bounds.centerX()
                        val centerY = bounds.centerY()
                        canvas.drawText(translatedText, centerX, centerY, textPaint)
                        renderedCount++
                    }
                }

                Log.d(TAG, "✅ Text rendering completed: $renderedCount texts drawn")

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "===== Translation applied successfully in ${totalTime}ms =====")

                inpaintedImage
            } catch (e: Exception) {
                Log.e(TAG, "❌ PHASE 5 FAILED: Text rendering error", e)
                Log.e(TAG, "Error details: ${e.message}")
                throw Exception("Phase 5 failed: Text rendering error - ${e.message}", e)
            }
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "===== Applying translation FAILED after ${totalTime}ms =====", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
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
