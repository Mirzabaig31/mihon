package mihon.feature.translation.di

import android.app.Application
import android.util.Log
import mihon.feature.translation.data.ModelManager
import mihon.feature.translation.data.TranslationCache
import mihon.feature.translation.data.TranslationManagerImpl
import mihon.feature.translation.data.detector.StubBubbleDetector
import mihon.feature.translation.data.detector.YOLOv10BubbleDetector
import android.renderscript.RenderScript
import mihon.feature.translation.data.inpainting.SimpleInpaintingEngine
import mihon.feature.translation.data.inpainting.StubInpaintingEngine
import mihon.feature.translation.data.ocr.MLKitOCREngine
import mihon.feature.translation.data.ocr.ModelDownloadManager
import mihon.feature.translation.data.ocr.OCREngineProvider
import mihon.feature.translation.data.ocr.PaddleModelDownloadManager
import mihon.feature.translation.data.ocr.PaddleOCREngine
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

        // Model Manager (Phase 3)
        addSingletonFactory {
            ModelManager(app)
        }

        // Phase 3: Bubble Detection (YOLOv10 with fallback to stub)
        addSingletonFactory<BubbleDetector> {
            try {
                val modelManager = get<ModelManager>()
                // Try to create YOLOv10 detector
                if (modelManager.isModelAvailable("yolov10_bubble_detection.tflite")) {
                    Log.d("TranslationModule", "Using YOLOv10 bubble detector")
                    YOLOv10BubbleDetector(app, modelManager)
                } else {
                    Log.w("TranslationModule", "YOLOv10 model not found, using stub detector")
                    StubBubbleDetector()
                }
            } catch (e: Exception) {
                Log.e("TranslationModule", "Failed to create YOLOv10 detector, using stub", e)
                StubBubbleDetector()
            }
        }

        // Model Download Managers for on-demand model downloads
        addSingletonFactory {
            ModelDownloadManager(app)
        }

        addSingletonFactory {
            PaddleModelDownloadManager(app, get())
        }

        // Phase 2: OCR Engines (ML Kit and PaddleOCR)
        // Create individual engines
        addSingletonFactory {
            MLKitOCREngine(app, get())
        }

        addSingletonFactory {
            PaddleOCREngine(app, get())
        }

        // OCR Engine Provider - selects engine based on user preference
        addSingletonFactory<OCREngine> {
            OCREngineProvider(
                mlKitEngine = get(),
                paddleEngine = get(),
                preferences = get(),
            )
        }

        // Phase 4A: Simple Inpainting (blur-based MVP)
        // Will be upgraded to LaMa in Phase 4B for production quality
        addSingletonFactory<InpaintingEngine> {
            try {
                val renderScript = RenderScript.create(app)
                Log.d("TranslationModule", "Using SimpleInpaintingEngine (blur-based)")
                SimpleInpaintingEngine(renderScript)
            } catch (e: Exception) {
                Log.e("TranslationModule", "Failed to create SimpleInpaintingEngine, using stub", e)
                StubInpaintingEngine()
            }
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
                modelDownloadManager = get(),
                paddleModelDownloadManager = get(),
            )
        }
    }
}
