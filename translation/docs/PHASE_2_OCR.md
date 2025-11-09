# Phase 2: ML Kit OCR Engine Implementation

## Overview

Integrate Google ML Kit's Text Recognition v2 for extracting text from comic speech bubbles. ML Kit provides free, on-device OCR with excellent support for CJK (Chinese, Japanese, Korean) languages.

## Goals

- ✅ On-device text recognition (no API costs)
- ✅ 88-92% accuracy for CJK languages
- ✅ 200-500ms processing time per bubble
- ✅ Support for Chinese, Korean, and Japanese
- ✅ Fallback to general Latin script OCR

## Components to Implement

### 1. ML Kit OCR Engine

**File:** `translation/src/main/java/mihon/feature/translation/data/ocr/MLKitOCREngine.kt`

```kotlin
class MLKitOCREngine @Inject constructor() : OCREngine {

    private val chineseRecognizer: TextRecognizer
    private val koreanRecognizer: TextRecognizer
    private val latinRecognizer: TextRecognizer

    override suspend fun extractText(
        image: Bitmap,
        regions: List<RectF>,
        language: Language,
    ): List<OCRResult>
}
```

**Key Features:**
- Lazy initialization of language-specific recognizers
- Automatic model download on first use (~3-5MB per language)
- Confidence scoring for quality control
- Bounding box extraction for precise text location
- Error handling with graceful fallbacks

### 2. PaddleOCR Engine (Optional High-Accuracy Fallback)

**File:** `translation/src/main/java/mihon/feature/translation/data/ocr/PaddleOCREngine.kt`

```kotlin
class PaddleOCREngine @Inject constructor(
    private val modelManager: ModelManager
) : OCREngine {

    private lateinit var predictorChinese: Predictor
    private lateinit var predictorKorean: Predictor

    // 92-96% accuracy but larger models (~120MB each)
}
```

**Trade-offs:**
- Higher accuracy (92-96% vs 88-92%)
- Slower processing (500-1000ms vs 200-500ms)
- Larger storage footprint (250MB vs 0MB)
- Recommended only for high-end devices

### 3. Hybrid OCR Engine

**File:** `translation/src/main/java/mihon/feature/translation/data/ocr/HybridOCREngine.kt`

```kotlin
class HybridOCREngine @Inject constructor(
    private val mlKit: MLKitOCREngine,
    private val paddle: PaddleOCREngine,
    private val preferences: TranslationPreferences
) : OCREngine {

    // Smart selection based on device capability and user preference
}
```

## Implementation Steps

### Step 1: Add Dependencies

Update `translation/build.gradle.kts`:

```kotlin
dependencies {
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.0") // Latin

    // Optional: PaddleOCR (for high accuracy)
    // implementation("com.baidu.paddle:paddle-lite:2.11.0")
    // implementation("io.github.mymonstercat:rapidocr:1.0.0")
}
```

### Step 2: Implement MLKitOCREngine

**Core Logic:**

1. **Crop Image Regions**
   ```kotlin
   private fun cropBitmap(source: Bitmap, rect: RectF): Bitmap {
       val x = rect.left.toInt().coerceIn(0, source.width - 1)
       val y = rect.top.toInt().coerceIn(0, source.height - 1)
       val width = (rect.right - rect.left).toInt()
       val height = (rect.bottom - rect.top).toInt()
       return Bitmap.createBitmap(source, x, y, width, height)
   }
   ```

2. **Select Recognizer by Language**
   ```kotlin
   private fun getRecognizer(language: Language): TextRecognizer {
       return when (language) {
           Language.CHINESE_SIMPLIFIED,
           Language.CHINESE_TRADITIONAL -> chineseRecognizer
           Language.KOREAN -> koreanRecognizer
           Language.JAPANESE -> chineseRecognizer // Uses Chinese recognizer
           else -> latinRecognizer
       }
   }
   ```

3. **Process Each Region**
   ```kotlin
   regions.map { region ->
       val cropped = cropBitmap(image, region)
       val inputImage = InputImage.fromBitmap(cropped, 0)

       val visionText = recognizer.process(inputImage).await()

       OCRResult(
           text = visionText.text,
           confidence = calculateConfidence(visionText),
           boundingBox = region,
           textBlocks = visionText.textBlocks.map { ... }
       )
   }
   ```

4. **Calculate Confidence**
   ```kotlin
   private fun calculateConfidence(visionText: Text): Float {
       if (visionText.textBlocks.isEmpty()) return 0f
       return visionText.textBlocks
           .mapNotNull { it.confidence }
           .average()
           .toFloat()
   }
   ```

### Step 3: Update Dependency Injection

**File:** `translation/di/TranslationModule.kt`

```kotlin
// Replace stub with real implementation
addSingletonFactory<OCREngine> {
    val preferences = get<TranslationPreferences>()

    when (preferences.ocrProvider().get()) {
        TranslationPreferences.OCR_ML_KIT -> MLKitOCREngine()
        TranslationPreferences.OCR_PADDLE -> PaddleOCREngine(get())
        else -> HybridOCREngine(
            mlKit = MLKitOCREngine(),
            paddle = PaddleOCREngine(get()),
            preferences = preferences
        )
    }
}
```

### Step 4: Testing

**Unit Tests:**
```kotlin
@Test
fun testMLKitOCREngine_Japanese() = runTest {
    val engine = MLKitOCREngine()
    val testBitmap = loadTestImage("japanese_text.png")
    val region = RectF(0f, 0f, 100f, 50f)

    val result = engine.extractTextFromRegion(
        testBitmap,
        region,
        Language.JAPANESE
    )

    assertTrue(result.text.isNotEmpty())
    assertTrue(result.confidence > 0.7f)
}
```

**Integration Tests:**
```kotlin
@Test
fun testMLKitWithTranslationManager() = runTest {
    val manager = TranslationManagerImpl(
        bubbleDetector = StubBubbleDetector(),
        ocrEngine = MLKitOCREngine(),
        translator = GeminiTranslator(...),
        inpaintingEngine = StubInpaintingEngine(),
        preferences = TranslationPreferences(...),
        cache = TranslationCache(...)
    )

    // Test full pipeline with real OCR
}
```

## Performance Optimization

### 1. Batch Processing
```kotlin
// Process multiple regions in parallel
val results = regions.mapAsync { region ->
    extractTextFromRegion(image, region, language)
}
```

### 2. Image Preprocessing
```kotlin
private fun preprocessImage(bitmap: Bitmap): Bitmap {
    // Enhance contrast for better OCR
    val enhanced = enhanceContrast(bitmap)

    // Denoise
    val denoised = denoise(enhanced)

    // Binarization for text
    return binarize(denoised)
}
```

### 3. Confidence Filtering
```kotlin
// Only accept high-confidence results
val filtered = ocrResults.filter { it.confidence > 0.6f }
```

## Error Handling

### 1. Model Download Failures
```kotlin
try {
    val result = recognizer.process(inputImage).await()
} catch (e: MlKitException) {
    when (e.errorCode) {
        MlKitException.UNAVAILABLE -> {
            // Model not downloaded yet
            Log.w(TAG, "ML Kit model downloading...")
            // Retry or use fallback
        }
        else -> throw e
    }
}
```

### 2. Low Confidence Handling
```kotlin
if (result.confidence < 0.5f) {
    // Try preprocessing
    val enhanced = preprocessImage(croppedImage)
    val retryResult = recognizer.process(enhanced).await()

    if (retryResult.confidence > result.confidence) {
        return retryResult
    }
}
```

## Testing Strategy

### Test Cases

1. **Japanese Manga**
   - Vertical text
   - Horizontal text
   - Mixed text styles
   - Handwritten-style fonts

2. **Chinese Manhua**
   - Simplified Chinese
   - Traditional Chinese
   - Complex characters

3. **Korean Manhwa**
   - Hangul text
   - Mixed Hangul/Hanja

4. **Edge Cases**
   - Very small text
   - Rotated text
   - Overlapping bubbles
   - Low contrast images

### Success Metrics

- ✅ Accuracy > 88% for CJK languages
- ✅ Processing time < 500ms per bubble
- ✅ Memory usage < 100MB
- ✅ Successful model auto-download
- ✅ Graceful degradation on failures

## Timeline

- **Week 1:** ML Kit integration and basic OCR
- **Week 2:** Preprocessing and optimization
- **Week 3:** Testing and refinement
- **Week 4:** Documentation and integration with Phase 1

## Dependencies on Other Phases

- **Phase 1:** ✅ Complete (Translation API)
- **Phase 3:** ⏳ Pending (Better bubble detection for better OCR accuracy)
- **Phase 4:** ⏳ Pending (Inpainting will improve visual quality)

## Cost Analysis

| Component | Cost | Storage | Network |
|-----------|------|---------|---------|
| ML Kit Chinese | $0 | ~5MB | Auto-download |
| ML Kit Korean | $0 | ~5MB | Auto-download |
| ML Kit Latin | $0 | ~3MB | Auto-download |
| **Total** | **$0** | **~13MB** | **One-time** |

## Next Phase Preview

After Phase 2 completion, we'll have:
- ✅ Translation API (Gemini)
- ✅ OCR Engine (ML Kit)
- ⏳ Bubble Detection (Phase 3 - YOLOv10)
- ⏳ Inpainting (Phase 4 - LaMa)

**Phase 3** will dramatically improve accuracy by detecting exact bubble locations instead of using full-page detection.

---

**Status:** Ready to implement
**Estimated Duration:** 4 weeks
**Complexity:** Medium
