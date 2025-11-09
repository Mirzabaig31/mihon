# Phase 4: LaMa Inpainting Engine Implementation

## Overview

Implement LaMa (Large Mask Inpainting) model for removing original text from comic pages. This creates clean backgrounds for rendering translated text, producing professional-quality results.

## Goals

- ✅ 9/10 visual quality (state-of-the-art inpainting)
- ✅ 2-5 seconds processing time per page
- ✅ On-device processing (no API costs)
- ✅ Clean background reconstruction
- ✅ Preserve artwork details around text

## Why LaMa?

**Advantages:**
- State-of-the-art inpainting quality (2021 model, still SOTA)
- Handles large masks (entire speech bubbles)
- Fast inference on mobile
- No training needed (pre-trained works great)

**Comparison:**
| Model | Quality | Speed | Size | Mobile |
|-------|---------|-------|------|--------|
| Simple Masking | 5/10 | <100ms | 0MB | ✅ |
| OpenCV Inpainting | 6/10 | 500ms | 10MB | ✅ |
| LaMa | 9/10 | 2-5s | 150MB | ✅ |
| Stable Diffusion | 9.5/10 | 10-20s | 2GB | ❌ |

## Implementation Steps

### Step 1: Add Dependencies

Update `translation/build.gradle.kts`:

```kotlin
dependencies {
    // TensorFlow Lite (already added in Phase 3)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Image processing
    implementation("androidx.renderscript:renderscript-toolkit:1.0.0")

    // Optional: ONNX Runtime (alternative to TFLite)
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")
}
```

### Step 2: Model Conversion

Convert LaMa PyTorch model to TFLite:

```python
# Python script for conversion
import torch
import tensorflow as tf
from lama.models.ade20k import SegmentationModule

# Load pre-trained LaMa model
model = torch.hub.load('advimman/lama', 'LaMa')
model.eval()

# Convert to TorchScript
example_input = torch.randn(1, 4, 512, 512)  # [batch, channels (RGB+mask), h, w]
traced_model = torch.jit.trace(model, example_input)

# Export to ONNX
torch.onnx.export(
    traced_model,
    example_input,
    "lama_large.onnx",
    input_names=['input'],
    output_names=['output'],
    dynamic_axes={'input': {0: 'batch', 2: 'height', 3: 'width'}}
)

# Convert ONNX to TFLite
import onnx
from onnx_tf.backend import prepare

onnx_model = onnx.load("lama_large.onnx")
tf_rep = prepare(onnx_model)
tf_rep.export_graph("lama_saved_model")

converter = tf.lite.TFLiteConverter.from_saved_model("lama_saved_model")
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]

tflite_model = converter.convert()

with open('lama_large_512.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Step 3: Implement LamaInpaintingEngine

**File:** `translation/src/main/java/mihon/feature/translation/data/inpainting/LamaInpaintingEngine.kt`

**Core Components:**

#### 3.1 Model Initialization

```kotlin
class LamaInpaintingEngine @Inject constructor(
    private val context: Context,
    private val modelManager: ModelManager
) : InpaintingEngine {

    private lateinit var interpreter: Interpreter
    private val inputSize = 512 // LaMa optimal size

    init {
        loadModel()
    }

    private fun loadModel() {
        val modelBuffer = modelManager.getModel("lama_large_512.tflite")

        val options = Interpreter.Options().apply {
            // GPU acceleration
            if (isGPUAvailable()) {
                addDelegate(GpuDelegate(GpuDelegate.Options().apply {
                    setIsPrecisionLossAllowed(true)
                }))
            }

            // NNAPI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addDelegate(NnApiDelegate())
            }

            setNumThreads(4)
            setUseXNNPACK(true)
        }

        interpreter = Interpreter(modelBuffer, options)
    }

    override fun getName(): String = "LaMa Large 512"

    override fun isAvailable(): Boolean = ::interpreter.isInitialized
}
```

#### 3.2 Image + Mask Preprocessing

```kotlin
override suspend fun inpaint(
    image: Bitmap,
    masks: List<Bitmap>
): Bitmap = withContext(Dispatchers.Default) {

    val startTime = System.currentTimeMillis()

    // Combine all masks into one
    val combinedMask = combineMasks(masks, image.width, image.height)

    // Resize to model input size
    val resizedImage = Bitmap.createScaledBitmap(image, inputSize, inputSize, true)
    val resizedMask = Bitmap.createScaledBitmap(combinedMask, inputSize, inputSize, true)

    // Prepare model input: [1, 4, 512, 512] (RGB + mask)
    val imageArray = bitmapToFloatArray(resizedImage, normalize = true)
    val maskArray = bitmapToFloatArray(resizedMask, grayscale = true, normalize = true)

    // Concatenate image and mask
    val inputArray = FloatArray(1 * 4 * inputSize * inputSize)
    System.arraycopy(imageArray, 0, inputArray, 0, imageArray.size)
    System.arraycopy(maskArray, 0, inputArray, imageArray.size, maskArray.size)

    // Run inference
    val outputArray = FloatArray(1 * 3 * inputSize * inputSize)
    interpreter.run(inputArray, outputArray)

    // Convert output to bitmap
    val inpaintedBitmap = floatArrayToBitmap(outputArray, inputSize, inputSize)

    // Scale back to original size
    val result = Bitmap.createScaledBitmap(
        inpaintedBitmap,
        image.width,
        image.height,
        true
    )

    val processingTime = System.currentTimeMillis() - startTime
    Log.d(TAG, "Inpainting completed in ${processingTime}ms")

    result
}
```

#### 3.3 Mask Combination

```kotlin
private fun combineMasks(
    masks: List<Bitmap>,
    width: Int,
    height: Int
): Bitmap {

    if (masks.isEmpty()) {
        // Return empty mask
        return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    }

    // Create combined mask canvas
    val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    val canvas = Canvas(combined)

    val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 255
        isAntiAlias = true
    }

    // Draw all masks
    masks.forEach { mask ->
        canvas.drawBitmap(mask, 0f, 0f, paint)
    }

    // Optional: Dilate mask slightly to ensure full text coverage
    return dilateMask(combined, iterations = 2)
}

private fun dilateMask(mask: Bitmap, iterations: Int): Bitmap {
    var current = mask.copy(mask.config, true)

    repeat(iterations) {
        val dilated = Bitmap.createBitmap(
            current.width,
            current.height,
            Bitmap.Config.ALPHA_8
        )

        val canvas = Canvas(dilated)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        }

        canvas.drawBitmap(current, 0f, 0f, paint)
        current = dilated
    }

    return current
}
```

#### 3.4 Image Conversion Utilities

```kotlin
private fun bitmapToFloatArray(
    bitmap: Bitmap,
    grayscale: Boolean = false,
    normalize: Boolean = true
): FloatArray {

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    val channels = if (grayscale) 1 else 3
    val floatArray = FloatArray(bitmap.width * bitmap.height * channels)

    pixels.forEachIndexed { index, pixel ->
        if (grayscale) {
            // Convert to grayscale (0 = keep, 1 = inpaint)
            val gray = Color.red(pixel) / 255f
            floatArray[index] = if (normalize) gray else gray * 255f
        } else {
            // RGB channels
            val baseIndex = index * 3
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            floatArray[baseIndex] = if (normalize) r else r * 255f
            floatArray[baseIndex + 1] = if (normalize) g else g * 255f
            floatArray[baseIndex + 2] = if (normalize) b else b * 255f
        }
    }

    return floatArray
}

private fun floatArrayToBitmap(
    floatArray: FloatArray,
    width: Int,
    height: Int
): Bitmap {

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (i in pixels.indices) {
        val baseIndex = i * 3
        val r = (floatArray[baseIndex] * 255f).toInt().coerceIn(0, 255)
        val g = (floatArray[baseIndex + 1] * 255f).toInt().coerceIn(0, 255)
        val b = (floatArray[baseIndex + 2] * 255f).toInt().coerceIn(0, 255)

        pixels[i] = Color.rgb(r, g, b)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
```

### Step 4: Simple Fallback Engine

**File:** `translation/src/main/java/mihon/feature/translation/data/inpainting/SimpleMaskingEngine.kt`

```kotlin
class SimpleMaskingEngine : InpaintingEngine {

    override suspend fun inpaint(
        image: Bitmap,
        masks: List<Bitmap>
    ): Bitmap = withContext(Dispatchers.Default) {

        val result = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Simple white fill (fast, lower quality)
        masks.forEach { mask ->
            // Draw mask areas as white
            val pixels = IntArray(mask.width * mask.height)
            mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)

            pixels.forEachIndexed { index, pixel ->
                if (Color.alpha(pixel) > 128) {
                    val x = index % mask.width
                    val y = index / mask.width
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }

        result
    }

    override fun getName(): String = "Simple White Fill"
    override fun isAvailable(): Boolean = true
}
```

### Step 5: Smart Inpainting Engine

**File:** `translation/src/main/java/mihon/feature/translation/data/inpainting/SmartInpaintingEngine.kt`

```kotlin
class SmartInpaintingEngine @Inject constructor(
    private val lama: LamaInpaintingEngine,
    private val simple: SimpleMaskingEngine,
    private val preferences: TranslationPreferences,
    private val deviceCapability: DeviceCapabilityDetector
) : InpaintingEngine {

    override suspend fun inpaint(
        image: Bitmap,
        masks: List<Bitmap>
    ): Bitmap {

        val quality = preferences.processingQuality().get()
        val deviceTier = deviceCapability.getDeviceTier()

        return when {
            // User wants ultra-high quality
            quality == TranslationPreferences.QUALITY_ULTRA_HIGH ->
                lama.inpaint(image, masks)

            // User wants high quality and device can handle it
            quality == TranslationPreferences.QUALITY_HIGH &&
            deviceTier >= DeviceTier.MID_RANGE ->
                lama.inpaint(image, masks)

            // Balanced: use LaMa on high-end, simple on low-end
            quality == TranslationPreferences.QUALITY_BALANCED ->
                if (deviceTier == DeviceTier.HIGH_END) {
                    lama.inpaint(image, masks)
                } else {
                    simple.inpaint(image, masks)
                }

            // Fast mode or low-end device
            else -> simple.inpaint(image, masks)
        }
    }

    override fun getName(): String = "Smart Inpainting"
    override fun isAvailable(): Boolean = true
}
```

### Step 6: Update Dependency Injection

**File:** `translation/di/TranslationModule.kt`

```kotlin
// Replace stub with real implementations
addSingletonFactory<InpaintingEngine> {
    val preferences = get<TranslationPreferences>()

    when (preferences.inpaintingEngine().get()) {
        TranslationPreferences.INPAINTING_LAMA ->
            LamaInpaintingEngine(app, get())

        TranslationPreferences.INPAINTING_SIMPLE ->
            SimpleMaskingEngine()

        else -> SmartInpaintingEngine(
            lama = LamaInpaintingEngine(app, get()),
            simple = SimpleMaskingEngine(),
            preferences = preferences,
            deviceCapability = get()
        )
    }
}
```

## Performance Optimization

### 1. Tiled Inpainting for Large Images

```kotlin
private suspend fun inpaintTiled(
    image: Bitmap,
    mask: Bitmap,
    tileSize: Int = 512
): Bitmap = withContext(Dispatchers.Default) {

    val result = image.copy(Bitmap.Config.ARGB_8888, true)

    // Process in tiles
    val tilesX = (image.width + tileSize - 1) / tileSize
    val tilesY = (image.height + tileSize - 1) / tileSize

    for (y in 0 until tilesY) {
        for (x in 0 until tilesX) {
            val tileX = x * tileSize
            val tileY = y * tileSize
            val tileWidth = minOf(tileSize, image.width - tileX)
            val tileHeight = minOf(tileSize, image.height - tileY)

            // Check if tile has mask content
            if (hasMaskContent(mask, tileX, tileY, tileWidth, tileHeight)) {
                // Extract tile
                val imageTile = Bitmap.createBitmap(image, tileX, tileY, tileWidth, tileHeight)
                val maskTile = Bitmap.createBitmap(mask, tileX, tileY, tileWidth, tileHeight)

                // Inpaint tile
                val inpaintedTile = inpaintSingleTile(imageTile, maskTile)

                // Copy back to result
                val canvas = Canvas(result)
                canvas.drawBitmap(inpaintedTile, tileX.toFloat(), tileY.toFloat(), null)
            }
        }
    }

    result
}
```

### 2. Progressive Inpainting

```kotlin
suspend fun inpaintProgressive(
    image: Bitmap,
    masks: List<Bitmap>,
    onProgress: (Float) -> Unit
): Bitmap {

    val totalSteps = masks.size
    var currentStep = 0

    var result = image.copy(image.config, true)

    masks.forEach { mask ->
        result = inpaint(result, listOf(mask))
        currentStep++
        onProgress(currentStep.toFloat() / totalSteps)
    }

    return result
}
```

### 3. Background Processing

```kotlin
class InpaintingQueue @Inject constructor(
    private val engine: InpaintingEngine
) {

    private val processingQueue = Channel<InpaintingTask>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startProcessing()
    }

    private fun startProcessing() {
        scope.launch {
            for (task in processingQueue) {
                try {
                    val result = engine.inpaint(task.image, task.masks)
                    task.onComplete(result)
                } catch (e: Exception) {
                    task.onError(e)
                }
            }
        }
    }

    fun enqueue(task: InpaintingTask) {
        processingQueue.trySend(task)
    }
}

data class InpaintingTask(
    val image: Bitmap,
    val masks: List<Bitmap>,
    val onComplete: (Bitmap) -> Unit,
    val onError: (Exception) -> Unit
)
```

## Testing Strategy

### Test Cases

1. **Small Text Regions**
   - Single speech bubble
   - Small font size
   - Clean backgrounds

2. **Large Text Regions**
   - Full-page text
   - Multiple overlapping bubbles
   - Complex backgrounds

3. **Edge Cases**
   - Text near image borders
   - Text over detailed artwork
   - Colored backgrounds
   - Gradients

4. **Quality Validation**
   - No visible artifacts
   - Smooth background reconstruction
   - Color consistency
   - Edge blending

### Success Metrics

- ✅ Visual quality > 8/10 (manual evaluation)
- ✅ No visible text remnants
- ✅ Processing time < 5s per page
- ✅ Consistent results across devices
- ✅ Handles pages with 1-20+ bubbles

## Timeline

- **Week 1:** Model conversion and basic integration
- **Week 2:** Optimization and tiling support
- **Week 3:** Quality testing and refinement
- **Week 4:** Edge case handling and fallback logic

## Cost Analysis

| Component | Cost | Storage | Network |
|-----------|------|---------|---------|
| LaMa TFLite Model | $0 | 150MB | One-time download |
| Simple Fallback | $0 | 0MB | N/A |
| GPU Delegate | $0 | (shared) | N/A |
| **Total** | **$0** | **~150MB** | **One-time** |

## Integration Timeline

After Phase 4 completion:
- ✅ **Phase 1:** Translation API
- ✅ **Phase 2:** OCR Engine
- ✅ **Phase 3:** Bubble Detection
- ✅ **Phase 4:** Inpainting → **FULL PIPELINE 100% COMPLETE**

## Next: Phase 5 (Polish)

With all core components complete, Phase 5 focuses on:
- Advanced typesetting (text rendering)
- Full Reader UI integration
- Settings screen
- Performance optimization
- User testing and refinement

---

**Status:** Ready to implement after Phase 3
**Estimated Duration:** 4 weeks
**Complexity:** High (model conversion + GPU optimization)
