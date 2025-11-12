package mihon.feature.translation.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.feature.translation.domain.models.TranslationData
import java.io.File

/**
 * Cache for storing translation results
 * Uses file-based storage similar to ChapterCache
 */
class TranslationCache(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "translation_cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    suspend fun get(chapterId: Long, pageIndex: Int): TranslationData? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(chapterId, pageIndex)
            if (file.exists()) {
                val jsonString = file.readText()
                val cached = json.decodeFromString<CachedTranslation>(jsonString)

                // Check if cache is expired
                if (System.currentTimeMillis() - cached.timestamp > CACHE_EXPIRATION_MS) {
                    file.delete()
                    return@withContext null
                }

                return@withContext cached.toTranslationData()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cache", e)
            null
        }
    }

    suspend fun put(chapterId: Long, pageIndex: Int, data: TranslationData) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(chapterId, pageIndex)
            val cached = CachedTranslation.fromTranslationData(data)
            val jsonString = json.encodeToString(cached)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cache", e)
        }
    }

    suspend fun has(chapterId: Long, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val file = getCacheFile(chapterId, pageIndex)
        file.exists() && (System.currentTimeMillis() - file.lastModified()) < CACHE_EXPIRATION_MS
    }

    suspend fun clearChapter(chapterId: Long) = withContext(Dispatchers.IO) {
        try {
            val chapterDir = File(cacheDir, chapterId.toString())
            if (chapterDir.exists()) {
                chapterDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chapter cache", e)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all cache", e)
        }
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (e: Exception) {
            0L
        }
    }

    private fun getCacheFile(chapterId: Long, pageIndex: Int): File {
        val chapterDir = File(cacheDir, chapterId.toString()).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File(chapterDir, "page_$pageIndex.json")
    }

    companion object {
        private const val TAG = "TranslationCache"
        private const val CACHE_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}

/**
 * Serializable cache entry
 * We store a simplified version to avoid serialization complexity with Bitmap
 */
@Serializable
private data class CachedTranslation(
    val pageIndex: Int,
    val chapterId: Long,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val bubbles: List<CachedBubble>,
    val processingTimeMs: Long,
    val timestamp: Long,
) {
    companion object {
        fun fromTranslationData(data: TranslationData): CachedTranslation {
            return CachedTranslation(
                pageIndex = data.pageIndex,
                chapterId = data.chapterId,
                sourceLanguageCode = data.sourceLanguage.code,
                targetLanguageCode = data.targetLanguage.code,
                bubbles = data.bubbles.map { bubble ->
                    CachedBubble(
                        boundingBox = CachedRect(
                            left = bubble.bubble.boundingBox.left,
                            top = bubble.bubble.boundingBox.top,
                            right = bubble.bubble.boundingBox.right,
                            bottom = bubble.bubble.boundingBox.bottom,
                        ),
                        originalText = bubble.ocrResult.text,
                        translatedText = bubble.translation.translatedText,
                        confidence = bubble.translation.confidence,
                        // Store bubble metadata for rendering
                        bubbleType = bubble.bubble.type.name,
                        textStyle = bubble.bubble.textStyle.name,
                        ocrConfidence = bubble.ocrResult.confidence,
                    )
                },
                processingTimeMs = data.processingTimeMs,
                timestamp = data.timestamp,
            )
        }
    }
}

@Serializable
private data class CachedBubble(
    val boundingBox: CachedRect,
    val originalText: String,
    val translatedText: String,
    val confidence: Float,
    // NEW: Store bubble metadata for proper rendering
    val bubbleType: String = "SPEECH",
    val textStyle: String = "HORIZONTAL",
    val ocrConfidence: Float = 0.0f,
)

@Serializable
private data class CachedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private fun CachedTranslation.toTranslationData(): TranslationData {
    return TranslationData(
        pageIndex = pageIndex,
        chapterId = chapterId,
        sourceLanguage = mihon.feature.translation.domain.models.Language.fromCode(sourceLanguageCode)
            ?: mihon.feature.translation.domain.models.Language.JAPANESE,
        targetLanguage = mihon.feature.translation.domain.models.Language.fromCode(targetLanguageCode)
            ?: mihon.feature.translation.domain.models.Language.ENGLISH,
        // FIXED: Restore full bubble list from cache
        bubbles = bubbles.map { cached ->
            mihon.feature.translation.domain.models.TranslatedBubble(
                bubble = mihon.feature.translation.domain.models.SpeechBubble(
                    boundingBox = android.graphics.RectF(
                        cached.boundingBox.left,
                        cached.boundingBox.top,
                        cached.boundingBox.right,
                        cached.boundingBox.bottom,
                    ),
                    confidence = cached.confidence,
                    type = try {
                        mihon.feature.translation.domain.models.BubbleType.valueOf(cached.bubbleType)
                    } catch (e: Exception) {
                        mihon.feature.translation.domain.models.BubbleType.SPEECH
                    },
                    textStyle = try {
                        mihon.feature.translation.domain.models.TextStyle.valueOf(cached.textStyle)
                    } catch (e: Exception) {
                        mihon.feature.translation.domain.models.TextStyle.HORIZONTAL
                    },
                ),
                ocrResult = mihon.feature.translation.domain.models.OCRResult(
                    text = cached.originalText,
                    confidence = cached.ocrConfidence,
                    boundingBox = android.graphics.RectF(
                        cached.boundingBox.left,
                        cached.boundingBox.top,
                        cached.boundingBox.right,
                        cached.boundingBox.bottom,
                    ),
                    textBlocks = emptyList(),
                    error = null,
                ),
                translation = mihon.feature.translation.domain.models.TranslationResult(
                    originalText = cached.originalText,
                    translatedText = cached.translatedText,
                    confidence = cached.confidence,
                    error = null,
                ),
            )
        },
        processingTimeMs = processingTimeMs,
        timestamp = timestamp,
    )
}
