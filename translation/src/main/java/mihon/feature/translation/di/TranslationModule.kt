package mihon.feature.translation.di

import android.app.Application
import mihon.feature.translation.data.TranslationCache
import mihon.feature.translation.data.TranslationManagerImpl
import mihon.feature.translation.data.detector.StubBubbleDetector
import mihon.feature.translation.data.inpainting.StubInpaintingEngine
import mihon.feature.translation.data.ocr.StubOCREngine
import mihon.feature.translation.data.translator.GeminiTranslator
import mihon.feature.translation.domain.BubbleDetector
import mihon.feature.translation.domain.InpaintingEngine
import mihon.feature.translation.domain.OCREngine
import mihon.feature.translation.domain.TranslationManager
import mihon.feature.translation.domain.TranslationPreferences
import mihon.feature.translation.domain.TranslatorAPI
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class TranslationModule(private val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {

        // Preferences
        addSingletonFactory {
            TranslationPreferences(get<PreferenceStore>())
        }

        // Cache
        addSingletonFactory {
            TranslationCache(app)
        }

        // Phase 1: Stub implementations for detector, OCR, and inpainting
        // These will be replaced in later phases
        addSingletonFactory<BubbleDetector> {
            StubBubbleDetector()
        }

        addSingletonFactory<OCREngine> {
            StubOCREngine()
        }

        addSingletonFactory<InpaintingEngine> {
            StubInpaintingEngine()
        }

        // Phase 1: Gemini translator (fully functional)
        addSingletonFactory<TranslatorAPI> {
            val preferences = get<TranslationPreferences>()
            GeminiTranslator(
                apiKey = preferences.geminiApiKey().get(),
                client = get(),
            )
        }

        // Main Translation Manager
        addSingletonFactory<TranslationManager> {
            TranslationManagerImpl(
                bubbleDetector = get(),
                ocrEngine = get(),
                translator = get(),
                inpaintingEngine = get(),
                preferences = get(),
                cache = get(),
            )
        }
    }
}
