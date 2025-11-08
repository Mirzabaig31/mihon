# Phase 3: YOLOv10 Bubble Detection Implementation

## Overview

Implement state-of-the-art speech bubble detection using YOLOv10, converted to TensorFlow Lite for on-device inference. This is the critical component that enables precise text localization in comic pages.

## Goals

- ✅ 92-95% detection accuracy
- ✅ 200-400ms processing time per page
- ✅ On-device processing (no API costs)
- ✅ Support for multiple bubble types (speech, thought, narration)
- ✅ GPU acceleration support
- ✅ Generate precise masks for inpainting

## Why YOLOv10?

**Advantages over YOLOv8:**
- 3-5% higher accuracy
- Lower latency (~15% faster)
- Anchor-free design (better for irregular bubble shapes)
- Released May 2024 (latest SOTA)

**Comparison:**
| Model | Accuracy | Speed | Size | Mobile Support |
|-------|----------|-------|------|----------------|
| YOLOv8n | 82-88% | 150ms | 6MB | ✅ Excellent |
| YOLOv8m | 87-92% | 250ms | 25MB | ✅ Good |
| YOLOv10 | 92-95% | 300ms | 65MB | ✅ Good |

## Pre-trained Models

### Recommended: Comic-Specialized YOLOv10

**Source:** Hugging Face / GitHub

1. **ogkalu/comic-speech-bubble-detector-yolov8m**
   - Trained on manga, webtoon, and manhua
   - Size: 45.78 MB
   - Accuracy: 85-90%

2. **mnemic/comic_speechbubble_detector_yolov8m**
   - Multi-style comics
   - Size: 90 MB
   - Accuracy: 87-92%

3. **Custom YOLOv10** (Train your own)
   - Maximum accuracy: 92-95%
   - Size: 65 MB
   - Dataset: 10,000+ annotated comic pages

## Implementation Steps

### Step 1: Add Dependencies

Update `translation/build.gradle.kts`:

```kotlin
dependencies {
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // GPU delegate for acceleration
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // NNAPI delegate (hardware acceleration)
    implementation("org.tensorflow:tensorflow-lite-nnapi-delegate:2.14.0")
}
```

### Step 2: Model Conversion

Convert YOLOv10 PyTorch model to TFLite:

```python
# Python script for conversion
from ultralytics import YOLO
import tensorflow as tf

# Load YOLOv10 model
model = YOLO('yolov10n.pt')

# Export to TFLite
model.export(format='tflite', int8=True, imgsz=1024)

# Optimize for mobile
converter = tf.lite.TFLiteConverter.from_saved_model('yolov10_saved_model')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]

tflite_model = converter.convert()

# Save optimized model
with open('yolov10_comic_detection.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Step 3: Implement YOLOv10BubbleDetector

**File:** `translation/src/main/java/mihon/feature/translation/data/detector/YOLOv10BubbleDetector.kt`

**Core Components:**

#### 3.1 Model Loading

```kotlin
class YOLOv10BubbleDetector @Inject constructor(
    private val context: Context,
    private val modelManager: ModelManager
) : BubbleDetector {

    private lateinit var interpreter: Interpreter
    private val inputSize = 1024 // YOLOv10 optimal for comics
    private val confidenceThreshold = 0.5f
    private val iouThreshold = 0.45f

    init {
        loadModel()
    }

    private fun loadModel() {
        val modelBuffer = modelManager.getModel("yolov10_comic_detection.tflite")

        val options = Interpreter.Options().apply {
            // GPU acceleration
            if (isGPUAvailable()) {
                addDelegate(GpuDelegate(GpuDelegate.Options().apply {
                    setIsPrecisionLossAllowed(true) // FP16 for speed
                }))
            }

            // NNAPI for hardware acceleration (Android 8.1+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addDelegate(NnApiDelegate())
            }

            // Thread count based on device
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))

            // Use XNNPack for CPU optimization
            setUseXNNPACK(true)
        }

        interpreter = Interpreter(modelBuffer, options)
    }

    private fun isGPUAvailable(): Boolean {
        return try {
            val delegate = GpuDelegate()
            delegate.close()
            true
        } catch (e: Exception) {
            Log.w(TAG, "GPU not available", e)
            false
        }
    }
}
```

#### 3.2 Image Preprocessing

```kotlin
private fun preprocessImage(image: Bitmap): FloatArray {
    // Resize to model input size
    val resized = Bitmap.createScaledBitmap(image, inputSize, inputSize, true)

    // Convert to float array [1, height, width, 3]
    val pixels = IntArray(inputSize * inputSize)
    resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

    val inputArray = FloatArray(1 * inputSize * inputSize * 3)
    var index = 0

    for (pixel in pixels) {
        // Normalize to [0, 1] and apply YOLOv10 preprocessing
        inputArray[index++] = ((pixel shr 16) and 0xFF) / 255.0f // R
        inputArray[index++] = ((pixel shr 8) and 0xFF) / 255.0f  // G
        inputArray[index++] = (pixel and 0xFF) / 255.0f          // B
    }

    return inputArray
}
```

#### 3.3 Inference Execution

```kotlin
override suspend fun detectBubbles(image: Bitmap): DetectionResult =
    withContext(Dispatchers.Default) {

    val startTime = System.currentTimeMillis()

    // Preprocess
    val inputArray = preprocessImage(image)

    // YOLOv10 output format: [1, num_detections, 7]
    // [x_center, y_center, width, height, confidence, class1_score, class2_score, ...]
    val outputArray = Array(1) { Array(8400) { FloatArray(7) } }

    // Run inference
    interpreter.run(inputArray, outputArray)

    val inferenceTime = System.currentTimeMillis() - startTime

    // Post-process
    val detections = postProcess(
        outputArray[0],
        image.width.toFloat(),
        image.height.toFloat()
    )

    // Generate masks
    val masks = generateMasks(detections, image.width, image.height)

    Log.d(TAG, "Detected ${detections.size} bubbles in ${inferenceTime}ms")

    DetectionResult(
        bubbles = detections,
        textRegions = detections.map { it.boundingBox },
        masks = masks,
        metadata = mapOf(
            "model" to "YOLOv10",
            "inferenceTime" to inferenceTime.toString(),
            "gpu_enabled" to isGPUAvailable().toString()
        )
    )
}
```

#### 3.4 Post-Processing (NMS)

```kotlin
private fun postProcess(
    output: Array<FloatArray>,
    imageWidth: Float,
    imageHeight: Float
): List<SpeechBubble> {

    val detections = mutableListOf<Detection>()

    // Filter by confidence
    for (detection in output) {
        val confidence = detection[4]
        if (confidence < confidenceThreshold) continue

        // Convert from normalized coordinates
        val centerX = detection[0] * imageWidth
        val centerY = detection[1] * imageHeight
        val width = detection[2] * imageWidth
        val height = detection[3] * imageHeight

        // Compute bounding box
        val left = (centerX - width / 2).coerceIn(0f, imageWidth)
        val top = (centerY - height / 2).coerceIn(0f, imageHeight)
        val right = (centerX + width / 2).coerceIn(0f, imageWidth)
        val bottom = (centerY + height / 2).coerceIn(0f, imageHeight)

        // Determine bubble type from class scores
        val bubbleType = classifyBubbleType(detection.sliceArray(5 until 7))

        detections.add(
            Detection(
                boundingBox = RectF(left, top, right, bottom),
                confidence = confidence,
                bubbleType = bubbleType
            )
        )
    }

    // Apply Non-Maximum Suppression
    return applyNMS(detections, iouThreshold)
        .map { det ->
            SpeechBubble(
                boundingBox = det.boundingBox,
                confidence = det.confidence,
                type = det.bubbleType,
                textStyle = inferTextStyle(det)
            )
        }
}

private fun applyNMS(
    detections: List<Detection>,
    iouThreshold: Float
): List<Detection> {

    val sorted = detections.sortedByDescending { it.confidence }
    val selected = mutableListOf<Detection>()

    for (det in sorted) {
        var shouldAdd = true

        for (selectedDet in selected) {
            val iou = calculateIoU(det.boundingBox, selectedDet.boundingBox)
            if (iou > iouThreshold) {
                shouldAdd = false
                break
            }
        }

        if (shouldAdd) {
            selected.add(det)
        }
    }

    return selected
}

private fun calculateIoU(box1: RectF, box2: RectF): Float {
    // Intersection
    val xLeft = maxOf(box1.left, box2.left)
    val yTop = maxOf(box1.top, box2.top)
    val xRight = minOf(box1.right, box2.right)
    val yBottom = minOf(box1.bottom, box2.bottom)

    if (xRight < xLeft || yBottom < yTop) return 0f

    val intersectionArea = (xRight - xLeft) * (yBottom - yTop)

    // Union
    val box1Area = box1.width() * box1.height()
    val box2Area = box2.width() * box2.height()
    val unionArea = box1Area + box2Area - intersectionArea

    return intersectionArea / unionArea
}
```

#### 3.5 Bubble Classification

```kotlin
private fun classifyBubbleType(classScores: FloatArray): BubbleType {
    val maxIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0

    return when (maxIndex) {
        0 -> BubbleType.SPEECH
        1 -> BubbleType.THOUGHT
        2 -> BubbleType.NARRATION
        else -> BubbleType.SOUND_EFFECT
    }
}

private fun inferTextStyle(detection: Detection): TextStyle {
    val aspectRatio = detection.boundingBox.width() / detection.boundingBox.height()

    return when {
        aspectRatio < 0.7 -> TextStyle.VERTICAL  // Tall = Japanese manga
        aspectRatio > 2.0 -> TextStyle.HORIZONTAL // Wide = English/Chinese
        else -> TextStyle.MIXED
    }
}
```

#### 3.6 Mask Generation

```kotlin
private fun generateMasks(
    detections: List<SpeechBubble>,
    width: Int,
    height: Int
): List<Bitmap> {

    return detections.map { bubble ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
            val canvas = Canvas(this)
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Draw rounded rectangle for speech bubble
            val radius = 20f
            canvas.drawRoundRect(bubble.boundingBox, radius, radius, paint)

            // Optional: Expand mask slightly for better inpainting
            val expanded = RectF(bubble.boundingBox).apply {
                inset(-5f, -5f)
            }
            canvas.drawRoundRect(expanded, radius, radius, paint)
        }
    }
}
```

### Step 4: Model Management

**File:** `translation/src/main/java/mihon/feature/translation/data/ModelManager.kt`

```kotlin
class ModelManager @Inject constructor(
    private val context: Context
) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "ml_models").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getModel(filename: String): ByteBuffer = withContext(Dispatchers.IO) {
        val modelFile = File(modelsDir, filename)

        if (!modelFile.exists()) {
            // Copy from assets or download
            copyFromAssets(filename, modelFile)
        }

        // Memory-map the model file
        FileInputStream(modelFile).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()

            return@withContext fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
        }
    }

    private fun copyFromAssets(filename: String, destFile: File) {
        context.assets.open("models/$filename").use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

### Step 5: Update Dependency Injection

**File:** `translation/di/TranslationModule.kt`

```kotlin
addSingletonFactory {
    ModelManager(app)
}

// Replace stub with real implementation
addSingletonFactory<BubbleDetector> {
    val preferences = get<TranslationPreferences>()

    when (preferences.bubbleDetector().get()) {
        TranslationPreferences.DETECTOR_YOLOV10 ->
            YOLOv10BubbleDetector(app, get())

        TranslationPreferences.DETECTOR_YOLOV8M ->
            YOLOv8BubbleDetector(app, get())

        else -> YOLOv10BubbleDetector(app, get())
    }
}
```

## Performance Optimization

### 1. Model Quantization

```python
# Use INT8 quantization for 4x smaller model
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.representative_dataset = representative_dataset_gen

# Result: 65MB → 16MB
```

### 2. Input Size Optimization

```kotlin
// Adaptive input size based on device
private fun getOptimalInputSize(): Int {
    val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .memoryClass

    return when {
        memoryClass >= 512 -> 1024  // High-end
        memoryClass >= 256 -> 640   // Mid-range
        else -> 416                  // Low-end
    }
}
```

### 3. Batch Processing

```kotlin
// Process multiple pages in background
suspend fun detectBubblesInChapter(pages: List<Bitmap>): List<DetectionResult> {
    return pages.mapAsync { page ->
        detectBubbles(page)
    }
}
```

## Testing Strategy

### Test Cases

1. **Manga (Japanese)**
   - Vertical speech bubbles
   - Action sound effects
   - Thought bubbles
   - Multi-panel pages

2. **Manhua (Chinese)**
   - Horizontal speech bubbles
   - Narrative boxes
   - Mixed styles

3. **Manhwa (Korean)**
   - Webtoon vertical scroll format
   - Long vertical bubbles

4. **Edge Cases**
   - Overlapping bubbles
   - Irregular shapes
   - Very small bubbles
   - Non-bubble text

### Success Metrics

- ✅ Precision > 90% (few false positives)
- ✅ Recall > 85% (catches most bubbles)
- ✅ Processing time < 400ms per page
- ✅ GPU acceleration working on supported devices
- ✅ Handles pages with 1-20+ bubbles

## Timeline

- **Week 1:** Model conversion and basic integration
- **Week 2:** Post-processing and NMS implementation
- **Week 3:** GPU optimization and testing
- **Week 4:** Edge case handling and refinement

## Cost Analysis

| Component | Cost | Storage | Network |
|-----------|------|---------|---------|
| YOLOv10 TFLite Model | $0 | 65MB (or 16MB quantized) | One-time download |
| GPU Delegate | $0 | 2MB | Bundled |
| NNAPI Delegate | $0 | Built-in | N/A |
| **Total** | **$0** | **~67MB** | **One-time** |

## Integration with Other Phases

Once Phase 3 is complete:
- ✅ **Phase 1:** Translation API ready
- ✅ **Phase 2:** OCR engine ready
- ✅ **Phase 3:** Bubble detection ready → **FULL PIPELINE 75% COMPLETE**
- ⏳ **Phase 4:** Inpainting (final visual polish)

## Troubleshooting

### Common Issues

1. **Model fails to load**
   - Check file exists in assets/models/
   - Verify file size matches expected
   - Ensure .tflite extension

2. **Slow inference**
   - Enable GPU delegate
   - Reduce input size
   - Use quantized model

3. **Poor detection accuracy**
   - Adjust confidence threshold
   - Retrain on more diverse dataset
   - Preprocess images (contrast enhancement)

---

**Status:** Ready to implement after Phase 2
**Estimated Duration:** 4 weeks
**Complexity:** High (model conversion + optimization)
