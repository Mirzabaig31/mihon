package mihon.feature.translation.domain

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun translationEnabled() = preferenceStore.getBoolean(
        "translation_enabled",
        false,
    )

    fun sourceLanguage() = preferenceStore.getString(
        "translation_source_language",
        "ja",
    )

    fun targetLanguage() = preferenceStore.getString(
        "translation_target_language",
        "en",
    )

    fun translatorProvider() = preferenceStore.getString(
        "translation_provider",
        "gemini",
    )

    fun ocrProvider() = preferenceStore.getString(
        "ocr_provider",
        "ml_kit",
    )

    fun bubbleDetector() = preferenceStore.getString(
        "bubble_detector",
        "yolov10",
    )

    fun inpaintingEngine() = preferenceStore.getString(
        "inpainting_engine",
        "lama",
    )

    fun showOriginalText() = preferenceStore.getBoolean(
        "translation_show_original",
        false,
    )

    fun autoTranslate() = preferenceStore.getBoolean(
        "translation_auto_translate",
        false,
    )

    fun geminiApiKey() = preferenceStore.getString(
        "translation_gemini_api_key",
        "",
    )

    fun googleCloudApiKey() = preferenceStore.getString(
        "translation_google_cloud_api_key",
        "",
    )

    fun openAiApiKey() = preferenceStore.getString(
        "translation_openai_api_key",
        "",
    )

    fun openAiBaseUrl() = preferenceStore.getString(
        "translation_openai_base_url",
        "https://api.deepseek.com",
    )

    fun openAiModelName() = preferenceStore.getString(
        "translation_openai_model",
        "deepseek-chat",
    )

    fun cacheEnabled() = preferenceStore.getBoolean(
        "translation_cache_enabled",
        true,
    )

    fun cacheExpirationDays() = preferenceStore.getInt(
        "translation_cache_expiration_days",
        30,
    )

    fun processingQuality() = preferenceStore.getString(
        "translation_processing_quality",
        "balanced",
    )

    // Model download preferences
    fun autoDownloadModels() = preferenceStore.getBoolean(
        "translation_auto_download_models",
        false, // Default to manual download for user control
    )

    fun downloadOnWifiOnly() = preferenceStore.getBoolean(
        "translation_download_wifi_only",
        true, // Default to WiFi only to save data
    )

    fun downloadedModels() = preferenceStore.getStringSet(
        "translation_downloaded_models",
        emptySet(),
    )

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_GOOGLE_CLOUD = "google_cloud"
        const val PROVIDER_OPENAI_COMPAT = "openai_compat"
        const val PROVIDER_ML_KIT = "ml_kit"
        const val PROVIDER_AUTO = "auto"

        const val OCR_ML_KIT = "ml_kit"
        const val OCR_PADDLE = "paddle"
        const val OCR_CLOUD = "cloud"
        const val OCR_AUTO = "auto"

        const val DETECTOR_YOLOV10 = "yolov10"
        const val DETECTOR_YOLOV8M = "yolov8m"
        const val DETECTOR_YOLOV8N = "yolov8n"

        const val INPAINTING_LAMA = "lama"
        const val INPAINTING_DIFFUSION = "diffusion"
        const val INPAINTING_SIMPLE = "simple"

        const val QUALITY_ULTRA_HIGH = "ultra_high"
        const val QUALITY_HIGH = "high"
        const val QUALITY_BALANCED = "balanced"
        const val QUALITY_FAST = "fast"
    }
}
