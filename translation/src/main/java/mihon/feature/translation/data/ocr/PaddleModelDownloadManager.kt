package mihon.feature.translation.data.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import mihon.feature.translation.domain.models.Language
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Manages PaddleOCR model downloads
 * Downloads detection and recognition models on-demand
 */
class PaddleModelDownloadManager(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val modelsDir = File(context.filesDir, "paddle_models")

    init {
        // Create models directory if it doesn't exist
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    /**
     * Check if detection model is downloaded
     */
    fun isDetectionModelDownloaded(): Boolean {
        val modelFile = File(modelsDir, "ch_PP-OCRv4_det_infer.nb")
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Check if recognition model for language is downloaded
     */
    fun isRecognitionModelDownloaded(language: Language): Boolean {
        val modelName = getRecognitionModelName(language)
        val modelFile = File(modelsDir, modelName)
        val dictFile = File(modelsDir, getDictFileName(language))
        return modelFile.exists() && modelFile.length() > 0 &&
            dictFile.exists() && dictFile.length() > 0
    }

    /**
     * Check if all models for a language are downloaded
     */
    suspend fun isModelDownloaded(language: Language): Boolean = withContext(Dispatchers.IO) {
        isDetectionModelDownloaded() && isRecognitionModelDownloaded(language)
    }

    /**
     * Download detection model
     */
    fun downloadDetectionModel(): Flow<PaddleModelDownloadProgress> = flow {
        emit(PaddleModelDownloadProgress.Downloading(modelType = "detection", progressPercent = 0))

        try {
            val modelUrl = DETECTION_MODEL_URL
            val modelFile = File(modelsDir, "ch_PP-OCRv4_det_infer.nb")

            downloadFile(modelUrl, modelFile) { progress ->
                emit(
                    PaddleModelDownloadProgress.Downloading(
                        modelType = "detection",
                        progressPercent = progress,
                    ),
                )
            }

            emit(PaddleModelDownloadProgress.Completed(modelType = "detection"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download detection model", e)
            emit(
                PaddleModelDownloadProgress.Failed(
                    modelType = "detection",
                    error = e.message ?: "Unknown error",
                ),
            )
        }
    }

    /**
     * Download recognition model for a specific language
     */
    fun downloadRecognitionModel(language: Language): Flow<PaddleModelDownloadProgress> = flow {
        val modelType = "recognition_${language.code}"
        emit(PaddleModelDownloadProgress.Downloading(modelType = modelType, progressPercent = 0))

        try {
            val modelUrl = getRecognitionModelUrl(language)
            val dictUrl = getDictUrl(language)
            val modelFile = File(modelsDir, getRecognitionModelName(language))
            val dictFile = File(modelsDir, getDictFileName(language))

            // Download model file
            emit(
                PaddleModelDownloadProgress.Downloading(
                    modelType = modelType,
                    progressPercent = 0,
                    message = "Downloading recognition model...",
                ),
            )
            downloadFile(modelUrl, modelFile) { progress ->
                emit(
                    PaddleModelDownloadProgress.Downloading(
                        modelType = modelType,
                        progressPercent = progress / 2, // 0-50% for model
                        message = "Downloading recognition model...",
                    ),
                )
            }

            // Download dictionary file
            emit(
                PaddleModelDownloadProgress.Downloading(
                    modelType = modelType,
                    progressPercent = 50,
                    message = "Downloading dictionary...",
                ),
            )
            downloadFile(dictUrl, dictFile) { progress ->
                emit(
                    PaddleModelDownloadProgress.Downloading(
                        modelType = modelType,
                        progressPercent = 50 + progress / 2, // 50-100% for dict
                        message = "Downloading dictionary...",
                    ),
                )
            }

            emit(PaddleModelDownloadProgress.Completed(modelType = modelType))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download recognition model for $language", e)
            emit(
                PaddleModelDownloadProgress.Failed(
                    modelType = modelType,
                    error = e.message ?: "Unknown error",
                ),
            )
        }
    }

    /**
     * Download all models for a language (detection + recognition)
     */
    fun downloadAllModels(language: Language): Flow<PaddleModelDownloadProgress> = flow {
        // Download detection model if not present
        if (!isDetectionModelDownloaded()) {
            downloadDetectionModel().collect { emit(it) }
        }

        // Download recognition model
        downloadRecognitionModel(language).collect { emit(it) }
    }

    /**
     * Download a file from URL with progress tracking
     */
    private suspend fun downloadFile(
        url: String,
        destination: File,
        onProgress: suspend (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytes: Int

                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloadedBytes += bytes

                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        onProgress(progress)
                    }
                }
            }
        }
    }

    /**
     * Get detection model file path
     */
    fun getDetectionModelPath(): String {
        return File(modelsDir, "ch_PP-OCRv4_det_infer.nb").absolutePath
    }

    /**
     * Get recognition model file path for language
     */
    fun getRecognitionModelPath(language: Language): String {
        return File(modelsDir, getRecognitionModelName(language)).absolutePath
    }

    /**
     * Get dictionary file path for language
     */
    fun getDictPath(language: Language): String {
        return File(modelsDir, getDictFileName(language)).absolutePath
    }

    /**
     * Delete all downloaded models
     */
    suspend fun clearAllModels() = withContext(Dispatchers.IO) {
        modelsDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Delete models for a specific language
     */
    suspend fun clearLanguageModels(language: Language) = withContext(Dispatchers.IO) {
        File(modelsDir, getRecognitionModelName(language)).delete()
        File(modelsDir, getDictFileName(language)).delete()
    }

    private fun getRecognitionModelName(language: Language): String {
        return when (language) {
            Language.JAPANESE -> "japan_PP-OCRv4_rec_infer.nb"
            Language.CHINESE_SIMPLIFIED,
            Language.CHINESE_TRADITIONAL,
            -> "chinese_cht_PP-OCRv4_rec_infer.nb"
            Language.KOREAN -> "korean_PP-OCRv4_rec_infer.nb"
            else -> "en_PP-OCRv4_rec_infer.nb"
        }
    }

    private fun getDictFileName(language: Language): String {
        return when (language) {
            Language.JAPANESE -> "japan_dict.txt"
            Language.CHINESE_SIMPLIFIED,
            Language.CHINESE_TRADITIONAL,
            -> "chinese_cht_dict.txt"
            Language.KOREAN -> "korean_dict.txt"
            else -> "en_dict.txt"
        }
    }

    private fun getRecognitionModelUrl(language: Language): String {
        // Note: These URLs should point to your model hosting server
        // PaddleOCR models are typically hosted on GitHub releases or custom servers
        val baseUrl = "https://paddleocr.bj.bcebos.com/PP-OCRv4/mobile"
        return when (language) {
            Language.JAPANESE -> "$baseUrl/japan_PP-OCRv4_rec_infer.nb"
            Language.CHINESE_SIMPLIFIED,
            Language.CHINESE_TRADITIONAL,
            -> "$baseUrl/chinese_cht_PP-OCRv4_rec_infer.nb"
            Language.KOREAN -> "$baseUrl/korean_PP-OCRv4_rec_infer.nb"
            else -> "$baseUrl/en_PP-OCRv4_rec_infer.nb"
        }
    }

    private fun getDictUrl(language: Language): String {
        val baseUrl = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/dict"
        return when (language) {
            Language.JAPANESE -> "$baseUrl/japan_dict.txt"
            Language.CHINESE_SIMPLIFIED,
            Language.CHINESE_TRADITIONAL,
            -> "$baseUrl/chinese_cht_dict.txt"
            Language.KOREAN -> "$baseUrl/korean_dict.txt"
            else -> "$baseUrl/en_dict.txt"
        }
    }

    companion object {
        private const val TAG = "PaddleModelDownloadManager"

        // Detection model URL (mobile version, ~50MB)
        private const val DETECTION_MODEL_URL =
            "https://paddleocr.bj.bcebos.com/PP-OCRv4/mobile/ch_PP-OCRv4_det_infer.nb"
    }
}

/**
 * Progress state for PaddleOCR model downloads
 */
sealed class PaddleModelDownloadProgress {
    abstract val modelType: String

    data class Downloading(
        override val modelType: String,
        val progressPercent: Int,
        val message: String? = null,
    ) : PaddleModelDownloadProgress()

    data class Completed(
        override val modelType: String,
    ) : PaddleModelDownloadProgress()

    data class Failed(
        override val modelType: String,
        val error: String,
    ) : PaddleModelDownloadProgress()
}
