package mihon.feature.translation.data.ocr

import android.content.Context
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mihon.feature.translation.domain.models.Language

/**
 * Manages ML Kit model downloads for different languages
 * Provides on-demand download functionality and availability checking
 */
class ModelDownloadManager(
    private val context: Context,
) {

    private val moduleInstallClient = ModuleInstall.getClient(context)

    /**
     * Check if all required models for a language are downloaded
     */
    suspend fun isModelDownloaded(language: Language): Boolean = withContext(Dispatchers.IO) {
        try {
            val recognizerOptions = when (language) {
                Language.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
                Language.CHINESE_SIMPLIFIED,
                Language.CHINESE_TRADITIONAL,
                -> ChineseTextRecognizerOptions.Builder().build()
                Language.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
                else -> TextRecognizerOptions.Builder().build()
            }

            val recognizer = TextRecognition.getClient(recognizerOptions)

            val response = moduleInstallClient
                .areModulesAvailable(recognizer)
                .await()

            response.areModulesAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check model availability for $language", e)
            false
        }
    }

    /**
     * Download models for a specific language
     * Returns a Flow that emits download progress updates
     */
    fun downloadModel(language: Language): Flow<ModelDownloadProgress> = callbackFlow {
        try {
            val recognizerOptions = when (language) {
                Language.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
                Language.CHINESE_SIMPLIFIED,
                Language.CHINESE_TRADITIONAL,
                -> ChineseTextRecognizerOptions.Builder().build()
                Language.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
                else -> TextRecognizerOptions.Builder().build()
            }

            val recognizer = TextRecognition.getClient(recognizerOptions)

            val installRequest = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .setListener { update ->
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING -> {
                            // Note: ModuleInstallStatusUpdate doesn't provide byte counts
                            // We can only report that download is in progress
                            trySend(
                                ModelDownloadProgress.Downloading(
                                    language = language,
                                    progressPercent = 0, // Progress percentage not available
                                    bytesDownloaded = 0,
                                    totalBytes = 0,
                                ),
                            )
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                            trySend(ModelDownloadProgress.Completed(language))
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                            trySend(
                                ModelDownloadProgress.Failed(
                                    language,
                                    "Installation failed: ${update.errorCode}",
                                ),
                            )
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                            trySend(ModelDownloadProgress.Cancelled(language))
                        }
                        else -> {
                            // Other states (pending, installing, etc.)
                            Log.d(TAG, "Model install state: ${update.installState}")
                        }
                    }
                }
                .build()

            moduleInstallClient.installModules(installRequest)
                .addOnSuccessListener {
                    Log.d(TAG, "Model download completed for $language")
                    trySend(ModelDownloadProgress.Completed(language))
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Model download failed for $language", e)
                    trySend(ModelDownloadProgress.Failed(language, e.message ?: "Unknown error"))
                    close(e)
                }

            awaitClose {
                // Cleanup if needed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start model download for $language", e)
            trySend(ModelDownloadProgress.Failed(language, e.message ?: "Unknown error"))
            close(e)
        }
    }

    /**
     * Download models for multiple languages
     * Returns a combined Flow of all download progress
     */
    fun downloadModels(languages: List<Language>): Flow<ModelDownloadProgress> = callbackFlow {
        try {
            val recognizers = languages.map { language ->
                when (language) {
                    Language.JAPANESE -> {
                        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                    }
                    Language.CHINESE_SIMPLIFIED,
                    Language.CHINESE_TRADITIONAL,
                    -> {
                        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    }
                    Language.KOREAN -> {
                        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                    }
                    else -> {
                        TextRecognition.getClient(TextRecognizerOptions.Builder().build())
                    }
                }
            }

            val requestBuilder = ModuleInstallRequest.newBuilder()
                .setListener { update ->
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING -> {
                            // Note: ModuleInstallStatusUpdate doesn't provide byte counts
                            // We can only report that download is in progress
                            // We can't tell which language this is for in batch mode
                            trySend(
                                ModelDownloadProgress.Downloading(
                                    language = languages.first(),
                                    progressPercent = 0, // Progress percentage not available
                                    bytesDownloaded = 0,
                                    totalBytes = 0,
                                ),
                            )
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                            languages.forEach { language ->
                                trySend(ModelDownloadProgress.Completed(language))
                            }
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                            languages.forEach { language ->
                                trySend(
                                    ModelDownloadProgress.Failed(
                                        language,
                                        "Installation failed: ${update.errorCode}",
                                    ),
                                )
                            }
                        }
                        else -> {
                            Log.d(TAG, "Batch model install state: ${update.installState}")
                        }
                    }
                }

            recognizers.forEach { requestBuilder.addApi(it) }
            val installRequest = requestBuilder.build()

            moduleInstallClient.installModules(installRequest)
                .addOnSuccessListener {
                    Log.d(TAG, "Batch model download completed")
                    languages.forEach { language ->
                        trySend(ModelDownloadProgress.Completed(language))
                    }
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Batch model download failed", e)
                    languages.forEach { language ->
                        trySend(ModelDownloadProgress.Failed(language, e.message ?: "Unknown error"))
                    }
                    close(e)
                }

            awaitClose {
                // Cleanup if needed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start batch model download", e)
            languages.forEach { language ->
                trySend(ModelDownloadProgress.Failed(language, e.message ?: "Unknown error"))
            }
            close(e)
        }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
    }
}

/**
 * Represents the progress of a model download
 */
sealed class ModelDownloadProgress {
    abstract val language: Language

    data class Downloading(
        override val language: Language,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelDownloadProgress()

    data class Completed(
        override val language: Language,
    ) : ModelDownloadProgress()

    data class Failed(
        override val language: Language,
        val error: String,
    ) : ModelDownloadProgress()

    data class Cancelled(
        override val language: Language,
    ) : ModelDownloadProgress()
}
