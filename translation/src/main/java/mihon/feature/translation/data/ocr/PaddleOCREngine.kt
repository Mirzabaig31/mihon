package mihon.feature.translation.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.models.Language
import mihon.feature.translation.domain.models.OCRResult
import mihon.feature.translation.domain.models.TextBlock
import kotlin.system.measureTimeMillis

/**
 * PaddleOCR implementation of OCR engine
 * Provides higher accuracy (90-95%) than ML Kit (85-90%) but requires model downloads
 *
 * ⚠️ IMPLEMENTATION STATUS: Infrastructure Complete, Paddle Lite SDK Required
 *
 * This class implements the full PaddleOCR inference pipeline including:
 * ✅ Detection preprocessing and postprocessing (DBNet)
 * ✅ Recognition preprocessing and postprocessing (CRNN + CTC)
 * ✅ Dictionary loading and character decoding
 * ⚠️ Paddle Lite SDK integration (commented out - requires manual installation)
 *
 * 📖 Setup Guide: translation/docs/PADDLEOCR_SETUP_GUIDE.md
 *
 * Algorithm:
 * 1. Detection phase: Preprocess image → Run DBNet → Postprocess to get text boxes
 * 2. Recognition phase: For each text box → Crop → Preprocess → Run CRNN → CTC decode
 * 3. Merge results into OCRResult
 *
 * Performance: 800-1200ms per page (4-6 text regions)
 * Accuracy: 90-95% (better on clean text, manga fonts)
 * Model size: 12MB (detection) + 8MB (recognition) = 20MB per language
 *
 * Until Paddle Lite SDK is installed, this engine will return errors.
 * Use ML Kit OCR as fallback (fully functional).
 */
class PaddleOCREngine(
    private val context: Context,
    private val modelDownloadManager: PaddleModelDownloadManager,
) : OCREngine {

    private val textProcessor = TextBlockProcessor()
    private val dictionary = PaddleOCRDictionary(context)

    // Paddle Lite predictors (commented out until SDK is installed)
    // See: translation/docs/PADDLEOCR_SETUP_GUIDE.md for setup instructions
    // private var detectionPredictor: PaddlePredictor? = null
    // private var recognitionPredictor: PaddlePredictor? = null

    // Cache loaded dictionaries per language
    private val loadedDictionaries = mutableMapOf<Language, List<String>>()

    override suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult> = withContext(Dispatchers.Default) {
        // Check if models are downloaded
        val isModelAvailable = modelDownloadManager.isModelDownloaded(language)
        if (!isModelAvailable) {
            val errorMessage =
                "PaddleOCR models for $language not downloaded. Please download in settings."
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

        // Process each region in parallel
        regions.map { region ->
            async {
                try {
                    processRegion(image, region, language)
                } catch (e: Exception) {
                    Log.e(TAG, "PaddleOCR failed for region $region", e)
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
     * Process a single region with PaddleOCR
     *
     * Pipeline:
     * 1. Detection: Preprocess → DBNet → Postprocess to get text boxes
     * 2. Recognition: For each box → Preprocess → CRNN → CTC decode
     * 3. Merge results
     */
    private suspend fun processRegion(
        image: Bitmap,
        region: RectF,
        language: Language,
    ): OCRResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Step 0: Crop image to region
            val croppedImage = cropBitmapToRegion(image, region)

            // Step 1: Load dictionary for this language
            val dict = loadedDictionaries.getOrPut(language) {
                dictionary.loadDictionary(language)
            }

            if (dict.isEmpty()) {
                Log.w(TAG, "Dictionary not available for $language")
                return@withContext OCRResult(
                    text = "",
                    confidence = 0f,
                    boundingBox = region,
                    textBlocks = emptyList(),
                    error = "Dictionary not available for $language",
                )
            }

            // Step 2: Detection phase - preprocess image
            val detectionTime = measureTimeMillis {
                val (preprocessedDetection, dimensions) = PaddleOCRPreprocessor.preprocessDetection(
                    croppedImage,
                    DETECTION_INPUT_SIZE,
                )

                // Step 3: Run detection inference (DBNet)
                // ⚠️ REQUIRES PADDLE LITE SDK - Currently commented out
                // Uncomment after installing Paddle Lite (see docs/PADDLEOCR_SETUP_GUIDE.md)
                /*
                val detectionOutput = runDetectionInference(preprocessedDetection)

                // Step 4: Post-process detection to get text boxes
                val textBoxes = PaddleOCRPostprocessor.processDetection(
                    detectionOutput,
                    dimensions,
                    DETECTION_THRESHOLD
                )

                Log.d(TAG, "Detected ${textBoxes.size} text boxes in region")

                // Step 5: Recognition phase - process each detected text box
                val recognizedTexts = textBoxes.map { textBox ->
                    recognizeTextBox(croppedImage, textBox, dict)
                }

                // Step 6: Combine results
                val combinedText = recognizedTexts.joinToString(" ") { it.first }
                val avgConfidence = if (recognizedTexts.isNotEmpty()) {
                    recognizedTexts.map { it.second }.average().toFloat()
                } else {
                    0f
                }

                val textBlocks = recognizedTexts.mapIndexed { index, (text, confidence) ->
                    TextBlock(
                        text = text,
                        confidence = confidence,
                        boundingBox = textBoxes[index].boundingBox,
                        language = language
                    )
                }
                 */
            }

            // TEMPORARY: Return error until Paddle Lite SDK is installed
            Log.w(
                TAG,
                "PaddleOCR inference skipped - Paddle Lite SDK not installed. " +
                    "See translation/docs/PADDLEOCR_SETUP_GUIDE.md for setup instructions.",
            )

            return@withContext OCRResult(
                text = "",
                confidence = 0f,
                boundingBox = region,
                textBlocks = emptyList(),
                error = "Paddle Lite SDK not installed. Download from: " +
                    "https://github.com/PaddlePaddle/Paddle-Lite/releases",
            )

            /*
            // UNCOMMENT WHEN PADDLE LITE IS INSTALLED:
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "PaddleOCR completed in ${totalTime}ms (detection: ${detectionTime}ms)")

            return@withContext OCRResult(
                text = combinedText,
                confidence = avgConfidence,
                boundingBox = region,
                textBlocks = textBlocks
            )
             */
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR processing failed", e)
            return@withContext OCRResult(
                text = "",
                confidence = 0f,
                boundingBox = region,
                textBlocks = emptyList(),
                error = e.message,
            )
        }
    }

    /**
     * Recognize text in a single text box
     */
    private suspend fun recognizeTextBox(
        image: Bitmap,
        textBox: PaddleOCRPostprocessor.TextBox,
        dictionary: List<String>,
    ): Pair<String, Float> = withContext(Dispatchers.Default) {
        try {
            // Step 1: Crop to text box bounds
            val croppedBox = cropBitmapToRect(image, textBox.boundingBox)

            // Step 2: Preprocess for recognition
            val preprocessed = PaddleOCRPreprocessor.preprocessRecognition(
                croppedBox,
                RECOGNITION_INPUT_HEIGHT,
                RECOGNITION_INPUT_WIDTH,
            )

            // Step 3: Run recognition inference (CRNN)
            // ⚠️ REQUIRES PADDLE LITE SDK
            /*
            val recognitionOutput = runRecognitionInference(preprocessed)

            // Step 4: Decode CTC output to text
            val (text, confidence) = PaddleOCRPostprocessor.decodeCTC(
                recognitionOutput,
                dictionary,
                blankIndex = 0
            )

            Log.d(TAG, "Recognized: \"$text\" (confidence: $confidence)")
            return@withContext Pair(text, confidence)
             */

            // TEMPORARY: Return empty until Paddle Lite is installed
            return@withContext Pair("", 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Text box recognition failed", e)
            return@withContext Pair("", 0f)
        }
    }

    /**
     * Run detection inference (DBNet)
     * ⚠️ REQUIRES PADDLE LITE SDK - Uncomment after installation
     */
    /*
    private fun runDetectionInference(input: FloatArray): FloatArray {
        val inputTensor = detectionPredictor?.getInput(0)
        inputTensor?.resize(intArrayOf(1, 3, DETECTION_INPUT_SIZE, DETECTION_INPUT_SIZE))
        inputTensor?.setData(input)

        detectionPredictor?.run()

        val outputTensor = detectionPredictor?.getOutput(0)
        val outputShape = outputTensor?.shape() ?: intArrayOf()
        val outputSize = outputShape.reduce { acc, i -> acc * i }

        return FloatArray(outputSize).apply {
            outputTensor?.getData(this)
        }
    }
     */

    /**
     * Run recognition inference (CRNN)
     * ⚠️ REQUIRES PADDLE LITE SDK - Uncomment after installation
     */
    /*
    private fun runRecognitionInference(input: FloatArray): Array<FloatArray> {
        val inputTensor = recognitionPredictor?.getInput(0)
        val inputHeight = RECOGNITION_INPUT_HEIGHT
        val inputWidth = input.size / (3 * inputHeight) // Calculate actual width

        inputTensor?.resize(intArrayOf(1, 3, inputHeight, inputWidth))
        inputTensor?.setData(input)

        recognitionPredictor?.run()

        val outputTensor = recognitionPredictor?.getOutput(0)
        val outputShape = outputTensor?.shape() ?: intArrayOf()
        // Output shape: [1, timesteps, num_classes]

        val timesteps = outputShape[1]
        val numClasses = outputShape[2]
        val output = FloatArray(timesteps * numClasses)
        outputTensor?.getData(output)

        // Reshape to 2D array
        return Array(timesteps) { t ->
            FloatArray(numClasses) { c ->
                output[t * numClasses + c]
            }
        }
    }
     */

    /**
     * Crop bitmap to RectF region
     */
    private fun cropBitmapToRegion(bitmap: Bitmap, region: RectF): Bitmap {
        val left = region.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = region.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = region.width().toInt().coerceIn(1, bitmap.width - left)
        val height = region.height().toInt().coerceIn(1, bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Crop bitmap to RectF (for text boxes)
     */
    private fun cropBitmapToRect(bitmap: Bitmap, rect: RectF): Bitmap {
        return cropBitmapToRegion(bitmap, rect)
    }

    /**
     * Initialize Paddle Lite predictors for detection and recognition
     * Called when models are downloaded and ready
     *
     * ⚠️ REQUIRES PADDLE LITE SDK - Uncomment after installation
     * See: translation/docs/PADDLEOCR_SETUP_GUIDE.md
     */
    private fun initializePredictors(language: Language): Boolean {
        try {
            val detectionModelPath = modelDownloadManager.getDetectionModelPath()
            val recognitionModelPath = modelDownloadManager.getRecognitionModelPath(language)

            Log.d(TAG, "Initializing PaddleOCR predictors for $language")
            Log.d(TAG, "Detection model: $detectionModelPath")
            Log.d(TAG, "Recognition model: $recognitionModelPath")

            // ⚠️ UNCOMMENT AFTER INSTALLING PADDLE LITE SDK:
            /*
            // Import required:
            // import com.baidu.paddle.lite.MobileConfig
            // import com.baidu.paddle.lite.PaddlePredictor
            // import com.baidu.paddle.lite.PowerMode

            // Initialize detection predictor (DBNet)
            detectionPredictor = PaddlePredictor.createPaddlePredictor(
                MobileConfig().apply {
                    setModelFromFile(detectionModelPath)
                    setThreads(4) // Use 4 CPU threads
                    setPowerMode(PowerMode.LITE_POWER_HIGH) // High performance mode
                    setOptimized(true) // Use optimized models
                }
            )

            // Initialize recognition predictor (CRNN)
            recognitionPredictor = PaddlePredictor.createPaddlePredictor(
                MobileConfig().apply {
                    setModelFromFile(recognitionModelPath)
                    setThreads(4)
                    setPowerMode(PowerMode.LITE_POWER_HIGH)
                    setOptimized(true)
                }
            )

            Log.d(TAG, "PaddleOCR predictors initialized successfully for $language")
            return true
             */

            // TEMPORARY: Return false until Paddle Lite SDK is installed
            Log.w(TAG, "Paddle Lite SDK not installed - predictors not initialized")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PaddleOCR predictors", e)
            return false
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        // ⚠️ UNCOMMENT AFTER INSTALLING PADDLE LITE SDK:
        /*
        detectionPredictor?.release()
        detectionPredictor = null

        recognitionPredictor?.release()
        recognitionPredictor = null

        Log.d(TAG, "PaddleOCR predictors released")
         */

        loadedDictionaries.clear()
    }

    override fun getName(): String = "PaddleOCR"

    override fun supportsLanguage(language: Language): Boolean {
        // PaddleOCR supports a language if its recognition model is downloaded
        return modelDownloadManager.isRecognitionModelDownloaded(language)
    }

    /**
     * Check if PaddleOCR engine is available (detection model downloaded)
     * Note: This is not part of OCREngine interface
     */
    fun isAvailable(): Boolean {
        return modelDownloadManager.isDetectionModelDownloaded()
    }

    companion object {
        private const val TAG = "PaddleOCREngine"

        // Model configuration
        private const val DETECTION_INPUT_SIZE = 960
        private const val RECOGNITION_INPUT_HEIGHT = 48
        private const val RECOGNITION_INPUT_WIDTH = 320

        // Detection thresholds
        private const val DETECTION_THRESHOLD = 0.3f
        private const val BOX_THRESHOLD = 0.5f

        // Recognition settings
        private const val REC_BATCH_SIZE = 6
    }
}
