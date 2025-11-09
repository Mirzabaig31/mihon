package mihon.feature.translation.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.OCRResult
import kotlin.math.max
import kotlin.math.min

/**
 * ML Kit implementation of OCR engine
 * Supports Japanese, Chinese, Korean, and Latin text recognition
 */
class MLKitOCREngine(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
) : OCREngine {

    private val recognizerProvider = TextRecognizerProvider()
    private val textProcessor = TextBlockProcessor()

    override suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult> = withContext(Dispatchers.Default) {
        // Check if model is downloaded before attempting OCR
        val isModelAvailable = modelDownloadManager.isModelDownloaded(language)
        if (!isModelAvailable) {
            val errorMessage =
                "ML Kit model for $language not downloaded. Please download it in settings."
            Log.w(TAG, errorMessage)
            return@withContext regions.map { region ->
                OCRResult(
                    text = "",
                    confidence = 0f,
                    boundingBox = region,
                    textBlocks = emptyList(),
                    error = errorMessage,
                )
            }
        }

        val recognizer = recognizerProvider.getRecognizer(language)

        // Process each region (speech bubble) in parallel for faster performance
        regions.map { region ->
            async {
                try {
                    processRegion(image, region, recognizer, language)
                } catch (e: Exception) {
                    Log.e(TAG, "OCR failed for region $region", e)
                    OCRResult(
                        text = "",
                        confidence = 0f,
                        boundingBox = region,
                        textBlocks = emptyList(),
                        error = e.message,
                    )
                }
            }
        }.awaitAll()
    }

    /**
     * Process a single region (speech bubble) with OCR
     */
    private suspend fun processRegion(
        image: Bitmap,
        region: RectF,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        language: Language,
    ): OCRResult = withContext(Dispatchers.IO) {
        // Crop image to region with padding to avoid cutting off text
        val croppedImage = cropBitmap(image, region)

        // Convert to ML Kit InputImage
        val inputImage = InputImage.fromBitmap(croppedImage, 0)

        // Process with ML Kit (suspends until complete)
        val visionText = try {
            recognizer.process(inputImage).await()
        } catch (e: MlKitException) {
            when (e.errorCode) {
                MlKitException.UNAVAILABLE -> {
                    // Model not downloaded yet - ML Kit will download automatically
                    Log.w(TAG, "ML Kit model downloading for $language, this may take a few seconds...", e)
                    throw Exception("ML Kit model downloading. Please wait and try again.", e)
                }
                MlKitException.NOT_ENOUGH_SPACE -> {
                    Log.e(TAG, "Not enough space to download ML Kit model", e)
                    throw Exception("Not enough storage space for OCR model", e)
                }
                else -> {
                    Log.e(TAG, "ML Kit OCR error: ${e.errorCode}", e)
                    throw e
                }
            }
        }

        // Process and sort text blocks
        processVisionText(visionText, region)
    }

    /**
     * Convert ML Kit vision text result to domain OCRResult
     */
    private fun processVisionText(
        visionText: Text,
        region: RectF,
    ): OCRResult {
        if (visionText.textBlocks.isEmpty()) {
            return OCRResult(
                text = "",
                confidence = 0f,
                boundingBox = region,
                textBlocks = emptyList(),
            )
        }

        // Detect text style (vertical/horizontal/mixed)
        val textStyle = TextStyleDetector.detectTextStyle(visionText.textBlocks)

        // Process and sort text blocks into correct reading order
        val processedBlocks = textProcessor.processTextBlocks(
            visionText.textBlocks,
            textStyle,
        )

        // Filter out very small text blocks (likely noise or furigana)
        val sizeFilteredBlocks = filterSmallTextBlocks(processedBlocks)

        // Filter out low confidence text blocks (likely OCR errors)
        val filteredBlocks = filterLowConfidenceBlocks(sizeFilteredBlocks)

        // Concatenate text in correct order
        val extractedText = filteredBlocks.joinToString("\n") { it.text }

        // Calculate average confidence
        val avgConfidence = if (filteredBlocks.isNotEmpty()) {
            filteredBlocks.mapNotNull { it.confidence }.average().toFloat()
        } else {
            0f
        }

        Log.d(TAG, "Extracted text from region: ${extractedText.take(50)}...")
        Log.d(TAG, "Text style: $textStyle, Blocks: ${filteredBlocks.size}, Confidence: $avgConfidence")

        return OCRResult(
            text = extractedText.trim(),
            confidence = avgConfidence,
            boundingBox = region,
            textBlocks = filteredBlocks,
        )
    }

    /**
     * Filter out very small text blocks that are likely noise or furigana
     * Small text (height < 30% of average) is removed
     */
    private fun filterSmallTextBlocks(
        blocks: List<mihon.feature.translation.domain.models.TextBlock>,
    ): List<mihon.feature.translation.domain.models.TextBlock> {
        if (blocks.isEmpty()) return blocks

        val averageHeight = blocks.map { it.boundingBox.height() }.average()
        val minHeight = averageHeight * MIN_TEXT_HEIGHT_RATIO

        return blocks.filter { block ->
            block.boundingBox.height() >= minHeight
        }
    }

    /**
     * Filter out low confidence text blocks that are likely OCR errors
     * Only blocks with confidence >= 0.6 are kept
     */
    private fun filterLowConfidenceBlocks(
        blocks: List<mihon.feature.translation.domain.models.TextBlock>,
    ): List<mihon.feature.translation.domain.models.TextBlock> {
        if (blocks.isEmpty()) return blocks

        return blocks.filter { block ->
            (block.confidence ?: 0f) >= MIN_CONFIDENCE_THRESHOLD
        }
    }

    /**
     * Crop bitmap to region with padding to avoid cutting text
     */
    private fun cropBitmap(image: Bitmap, region: RectF): Bitmap {
        val padding = CROP_PADDING

        val left = max(0, region.left.toInt() - padding)
        val top = max(0, region.top.toInt() - padding)
        val right = min(image.width, region.right.toInt() + padding)
        val bottom = min(image.height, region.bottom.toInt() + padding)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid crop region: $region")
            return image
        }

        return try {
            Bitmap.createBitmap(image, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bitmap", e)
            image
        }
    }

    override fun getName(): String = "ML Kit OCR"

    override fun isAvailable(): Boolean = true

    /**
     * Clean up resources
     * Call this when the OCR engine is no longer needed
     */
    fun close() {
        recognizerProvider.close()
    }

    companion object {
        private const val TAG = "MLKitOCREngine"
        private const val CROP_PADDING = 10 // pixels
        private const val MIN_TEXT_HEIGHT_RATIO = 0.3f // 30% of average height
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f // 60% confidence minimum
    }
}
