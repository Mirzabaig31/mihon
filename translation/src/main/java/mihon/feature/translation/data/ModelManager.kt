package mihon.feature.translation.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Manages loading and caching of ML models for translation features
 * Supports loading from assets or downloaded files
 */
class ModelManager(
    private val context: Context,
) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "ml_models").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Get a model as a memory-mapped ByteBuffer
     * @param filename Model filename (e.g., "yolov10_bubble_detection.tflite")
     * @return Memory-mapped ByteBuffer for TFLite interpreter
     */
    suspend fun getModel(filename: String): ByteBuffer = withContext(Dispatchers.IO) {
        val modelFile = File(modelsDir, filename)

        // If model doesn't exist in app files, try to copy from assets
        if (!modelFile.exists()) {
            Log.d(TAG, "Model not found in filesDir, attempting to copy from assets: $filename")
            copyFromAssets(filename, modelFile)
        }

        // Verify file exists and is valid
        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw IllegalStateException("Model file not found or empty: $filename")
        }

        Log.d(TAG, "Loading model: $filename (${modelFile.length() / 1024 / 1024}MB)")

        // Memory-map the model file for efficient loading
        FileInputStream(modelFile).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()

            return@withContext fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength,
            )
        }
    }

    /**
     * Copy model from assets to filesDir
     * @param filename Model filename in assets/models/
     * @param destFile Destination file
     */
    private fun copyFromAssets(filename: String, destFile: File) {
        try {
            val assetPath = "models/$filename"
            Log.d(TAG, "Copying model from assets: $assetPath")

            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Model copied successfully: ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model from assets: $filename", e)
            throw IllegalStateException("Model file not found in assets: models/$filename", e)
        }
    }

    /**
     * Check if a model is available (exists in filesDir or assets)
     * @param filename Model filename
     * @return true if model is available
     */
    fun isModelAvailable(filename: String): Boolean {
        val modelFile = File(modelsDir, filename)
        if (modelFile.exists() && modelFile.length() > 0) {
            return true
        }

        // Check if available in assets
        return try {
            context.assets.open("models/$filename").use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the size of a model file in bytes
     * @param filename Model filename
     * @return Size in bytes, or 0 if not found
     */
    fun getModelSize(filename: String): Long {
        val modelFile = File(modelsDir, filename)
        return if (modelFile.exists()) {
            modelFile.length()
        } else {
            0L
        }
    }

    /**
     * Delete a model file from filesDir
     * @param filename Model filename
     * @return true if deleted successfully
     */
    suspend fun deleteModel(filename: String): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(modelsDir, filename)
        if (modelFile.exists()) {
            Log.d(TAG, "Deleting model: $filename")
            modelFile.delete()
        } else {
            false
        }
    }

    /**
     * Clear all downloaded models
     */
    suspend fun clearAllModels() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing all models from filesDir")
        modelsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
                Log.d(TAG, "Deleted: ${file.name}")
            }
        }
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
