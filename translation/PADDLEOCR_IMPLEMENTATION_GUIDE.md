# PaddleOCR Implementation Guide

This document provides a comprehensive guide to completing the PaddleOCR implementation in `PaddleOCREngine.kt`.

## Current Status

✅ **Completed:**
- Model download infrastructure (`PaddleModelDownloadManager.kt`)
- API surface and integration points
- Dual OCR engine architecture (ML Kit + PaddleOCR)
- Dependency injection configuration

❌ **Not Implemented:**
- Paddle Lite predictor integration
- Image preprocessing
- DBNet detection post-processing
- CTC recognition decoding
- Dictionary loading and management

## Implementation Roadmap

### Phase 1: Paddle Lite Predictor Setup (~100 lines)

**File:** `PaddleOCREngine.kt`

**What to implement:**
```kotlin
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor

private var detectionPredictor: PaddlePredictor? = null
private var recognitionPredictor: PaddlePredictor? = null

private fun initializePredictors(language: Language): Boolean {
    try {
        val detectionModelPath = modelDownloadManager.getDetectionModelPath()
        val recognitionModelPath = modelDownloadManager.getRecognitionModelPath(language)

        // Initialize detection predictor
        val detConfig = MobileConfig().apply {
            setModelFromFile(detectionModelPath)
            setThreads(4) // Adjust based on device capabilities
            setPowerMode(PowerMode.LITE_POWER_HIGH)
        }
        detectionPredictor = PaddlePredictor.createPaddlePredictor(detConfig)

        // Initialize recognition predictor
        val recConfig = MobileConfig().apply {
            setModelFromFile(recognitionModelPath)
            setThreads(4)
            setPowerMode(PowerMode.LITE_POWER_HIGH)
        }
        recognitionPredictor = PaddlePredictor.createPaddlePredictor(recConfig)

        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize predictors", e)
        return false
    }
}
```

**Reference:**
- [Paddle Lite Android Demo](https://github.com/PaddlePaddle/Paddle-Lite-Demo/tree/master/ocr/android/app/cxx/ppocr_demo)
- [Paddle Lite API Docs](https://www.paddlepaddle.org.cn/lite/v2.13/api_reference/java_api_doc.html)

### Phase 2: Image Preprocessing (~200 lines)

**File:** `PaddleOCRPreprocessor.kt` (new file)

**Detection Preprocessing Algorithm:**
1. Resize image to 960x960 maintaining aspect ratio
2. Pad with zeros if needed
3. Normalize pixels: `(pixel - mean) / std` where mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]
4. Convert from HWC (Height, Width, Channel) to CHW format
5. Return as FloatArray

**Code Template:**
```kotlin
fun preprocessDetection(bitmap: Bitmap): Pair<FloatArray, Pair<Int, Int>> {
    // Target size
    val targetSize = 960

    // Calculate resize dimensions maintaining aspect ratio
    val (resizeW, resizeH) = calculateResizeShape(
        bitmap.width,
        bitmap.height,
        targetSize
    )

    // Resize bitmap
    val resized = Bitmap.createScaledBitmap(bitmap, resizeW, resizeH, true)

    // Create output array: CHW format, 3 channels, targetSize x targetSize
    val output = FloatArray(3 * targetSize * targetSize)

    // Normalization parameters (ImageNet)
    val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    // Convert to CHW and normalize
    for (y in 0 until resizeH) {
        for (x in 0 until resizeW) {
            val pixel = resized.getPixel(x, y)
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // CHW format: [C, H, W]
            output[0 * targetSize * targetSize + y * targetSize + x] = (r - mean[0]) / std[0]
            output[1 * targetSize * targetSize + y * targetSize + x] = (g - mean[1]) / std[1]
            output[2 * targetSize * targetSize + y * targetSize + x] = (b - mean[2]) / std[2]
        }
    }

    return Pair(output, Pair(resizeW, resizeH))
}

private fun calculateResizeShape(
    width: Int,
    height: Int,
    targetSize: Int
): Pair<Int, Int> {
    val ratio = width.toFloat() / height.toFloat()
    return if (ratio > 1) {
        val w = targetSize
        val h = (targetSize / ratio).toInt()
        Pair(w, h)
    } else {
        val h = targetSize
        val w = (targetSize * ratio).toInt()
        Pair(w, h)
    }
}
```

**Recognition Preprocessing Algorithm:**
1. Resize to height=48, width proportional (max 320)
2. Pad to width=320 if needed
3. Normalize same as detection
4. Convert to CHW format

**Reference:**
- [PaddleOCR Preprocessing Code](https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/lite/ocr_db_crnn.cc#L164)

### Phase 3: Detection Post-Processing (DBNet) (~300 lines)

**File:** `PaddleOCRPostprocessor.kt` (new file)

**DBNet Algorithm:**
1. **Binary Threshold**: Apply threshold to probability map
   ```kotlin
   val binaryMap = probMap.map { if (it > THRESHOLD) 1f else 0f }
   ```

2. **Find Contours**: Extract connected regions (requires contour detection)
   - Option A: Use OpenCV Android (`org.opencv:opencv:4.8.0`)
   - Option B: Implement custom contour detection

3. **Get Bounding Boxes**: Calculate min/max x,y for each contour

4. **Polygon Simplification**: Reduce contour points using Douglas-Peucker

5. **Score Calculation**: Calculate average probability inside each box

6. **Filtering**: Remove boxes with low scores or small areas

**Code Template (using OpenCV):**
```kotlin
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

fun postprocessDetection(
    probMap: FloatArray,
    width: Int,
    height: Int,
    originalWidth: Int,
    originalHeight: Int
): List<RectF> {
    // Convert to binary map
    val threshold = 0.3f
    val binaryMap = Mat(height, width, CvType.CV_8UC1)
    for (i in probMap.indices) {
        val value = if (probMap[i] > threshold) 255.toByte() else 0.toByte()
        binaryMap.put(i / width, i % width, value)
    }

    // Find contours
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(
        binaryMap,
        contours,
        hierarchy,
        Imgproc.RETR_LIST,
        Imgproc.CHAIN_APPROX_SIMPLE
    )

    // Process each contour
    val boxes = mutableListOf<RectF>()
    for (contour in contours) {
        // Calculate bounding rectangle
        val rect = Imgproc.boundingRect(contour)

        // Filter by size
        if (rect.width < 5 || rect.height < 5) continue

        // Calculate score (average probability in box)
        var scoreSum = 0f
        var scoreCount = 0
        for (y in rect.y until rect.y + rect.height) {
            for (x in rect.x until rect.x + rect.width) {
                scoreSum += probMap[y * width + x]
                scoreCount++
            }
        }
        val score = scoreSum / scoreCount

        // Filter by score
        if (score < 0.5f) continue

        // Scale back to original image size
        val scaleX = originalWidth.toFloat() / width
        val scaleY = originalHeight.toFloat() / height

        boxes.add(RectF(
            rect.x * scaleX,
            rect.y * scaleY,
            (rect.x + rect.width) * scaleX,
            (rect.y + rect.height) * scaleY
        ))
    }

    return boxes
}
```

**Alternative (No OpenCV):** Implement custom flood-fill contour detection

**Reference:**
- [PaddleOCR DBNet Post-processing](https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/db_postprocess.py)
- [OpenCV Android](https://opencv.org/android/)

### Phase 4: Recognition Decoding (CTC) (~150 lines)

**File:** `PaddleOCRPostprocessor.kt`

**CTC Decoding Algorithm:**
1. **Greedy Decode**: Take argmax at each timestep
   ```kotlin
   val indices = output.chunked(dictSize).map { it.indexOfMax() }
   ```

2. **Remove Blanks**: Filter out blank tokens (usually index 0)

3. **Remove Duplicates**: Merge consecutive identical characters

4. **Map to Characters**: Use dictionary to convert indices to chars

**Code Template:**
```kotlin
class CTCDecoder(private val dictionary: List<String>) {

    fun decode(output: FloatArray, seqLength: Int): Pair<String, Float> {
        val dictSize = dictionary.size + 1 // +1 for blank

        // Reshape output to [seqLength, dictSize]
        val logits = output.toList().chunked(dictSize)

        // Greedy decoding: argmax at each timestep
        val indices = logits.map { it.indexOfMax() }

        // Calculate confidence (average of max probabilities)
        val confidences = logits.map { softmax(it).maxOrNull() ?: 0f }
        val avgConfidence = confidences.average().toFloat()

        // Decode to text
        val text = decodeSequence(indices)

        return Pair(text, avgConfidence)
    }

    private fun decodeSequence(indices: List<Int>): String {
        val result = StringBuilder()
        var prevIndex = -1

        for (index in indices) {
            // Skip blank (0) and duplicates
            if (index == 0 || index == prevIndex) {
                prevIndex = index
                continue
            }

            // Map index to character
            if (index - 1 < dictionary.size) {
                result.append(dictionary[index - 1])
            }

            prevIndex = index
        }

        return result.toString()
    }

    private fun softmax(logits: List<Float>): List<Float> {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - maxLogit) }
        val sumExps = exps.sum()
        return exps.map { it / sumExps }
    }

    private fun List<Float>.indexOfMax(): Int {
        return indices.maxByOrNull { this[it] } ?: 0
    }
}
```

**Reference:**
- [PaddleOCR CTC Decoding](https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/rec_postprocess.py#L24)

### Phase 5: Dictionary Loading (~50 lines)

**File:** `PaddleOCREngine.kt`

**Dictionary Format:** One character per line in .txt file

**Code Template:**
```kotlin
private fun loadDictionary(dictPath: String): List<String> {
    return try {
        File(dictPath).readLines()
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load dictionary", e)
        emptyList()
    }
}

private val dictionaryCache = mutableMapOf<Language, List<String>>()

private fun getDictionary(language: Language): List<String> {
    return dictionaryCache.getOrPut(language) {
        val dictPath = modelDownloadManager.getDictPath(language)
        loadDictionary(dictPath)
    }
}
```

### Phase 6: Full Pipeline Integration

**File:** `PaddleOCREngine.kt` - `processRegion()` method

**Complete Pipeline:**
```kotlin
private suspend fun processRegion(
    image: Bitmap,
    region: RectF,
    language: Language,
): OCRResult = withContext(Dispatchers.IO) {
    // Initialize predictors if needed
    if (detectionPredictor == null || recognitionPredictor == null) {
        if (!initializePredictors(language)) {
            throw Exception("Failed to initialize PaddleOCR predictors")
        }
    }

    // 1. Crop to region
    val cropped = cropBitmap(image, region)

    // 2. Detection preprocessing
    val (detInput, resizeShape) = PaddleOCRPreprocessor.preprocessDetection(cropped)

    // 3. Run detection
    val detectionTensor = detectionPredictor!!.getInput(0)
    detectionTensor.resize(intArrayOf(1, 3, 960, 960))
    detectionTensor.setData(detInput)
    detectionPredictor!!.run()
    val detectionOutput = detectionPredictor!!.getOutput(0).floatData

    // 4. Detection post-processing
    val textBoxes = PaddleOCRPostprocessor.postprocessDetection(
        detectionOutput,
        resizeShape.first,
        resizeShape.second,
        cropped.width,
        cropped.height
    )

    // 5. Recognition for each text box
    val textBlocks = mutableListOf<TextBlock>()
    for (box in textBoxes) {
        val textImage = cropBitmap(cropped, box)

        // Recognition preprocessing
        val recInput = PaddleOCRPreprocessor.preprocessRecognition(textImage)

        // Run recognition
        val recognitionTensor = recognitionPredictor!!.getInput(0)
        recognitionTensor.resize(intArrayOf(1, 3, 48, 320))
        recognitionTensor.setData(recInput)
        recognitionPredictor!!.run()
        val recognitionOutput = recognitionPredictor!!.getOutput(0).floatData

        // CTC decoding
        val dictionary = getDictionary(language)
        val decoder = CTCDecoder(dictionary)
        val (text, confidence) = decoder.decode(recognitionOutput, seqLength = 25)

        textBlocks.add(TextBlock(
            text = text,
            boundingBox = box,
            confidence = confidence
        ))
    }

    // 6. Sort and combine results
    val sortedBlocks = textProcessor.processTextBlocks(textBlocks, detectTextStyle(textBlocks))
    val combinedText = sortedBlocks.joinToString("\n") { it.text }
    val avgConfidence = sortedBlocks.mapNotNull { it.confidence }.average().toFloat()

    return@withContext OCRResult(
        text = combinedText,
        confidence = avgConfidence,
        boundingBox = region,
        textBlocks = sortedBlocks
    )
}
```

## Dependencies to Add

```kotlin
// In translation/build.gradle.kts

// OpenCV for contour detection (optional but recommended)
implementation("org.opencv:opencv:4.8.0")

// Already have:
// implementation("io.github.PaddlePaddle:paddle-lite:2.13.0")
```

## Testing Strategy

1. **Unit Tests**: Test preprocessing/postprocessing separately
2. **Integration Tests**: Test with known images and expected outputs
3. **Benchmark**: Compare with ML Kit on same images
4. **Edge Cases**: Test with vertical text, mixed languages, low quality images

## Performance Optimization

1. **Model Caching**: Keep predictors initialized
2. **Batch Processing**: Process multiple text boxes together in recognition
3. **Thread Pool**: Reuse threads for parallel processing
4. **Memory Management**: Release bitmaps promptly

## Debugging Tips

1. Save intermediate images (cropped, preprocessed) for visual inspection
2. Log tensor shapes at each step
3. Validate output ranges (probabilities should be [0,1])
4. Compare with PaddleOCR Python output on same image

## Estimated Effort

- **Paddle Lite Setup**: 2-4 hours
- **Preprocessing**: 4-6 hours
- **DBNet Post-processing**: 8-12 hours (with OpenCV) or 20+ hours (custom)
- **CTC Decoding**: 4-6 hours
- **Integration & Testing**: 8-12 hours
- **Total**: 26-40 hours

## Alternative: Use PaddleOCR Android Demo

Instead of implementing from scratch, you can:
1. Clone [Paddle-Lite-Demo](https://github.com/PaddlePaddle/Paddle-Lite-Demo/tree/master/ocr/android)
2. Extract the native C++ OCR code
3. Wrap it with JNI in this project
4. Estimated effort: 8-12 hours

This is MUCH easier but adds native code complexity.

## References

- [PaddleOCR GitHub](https://github.com/PaddlePaddle/PaddleOCR)
- [Paddle Lite Android Demo](https://github.com/PaddlePaddle/Paddle-Lite-Demo/tree/master/ocr/android)
- [PaddleOCR Inference Documentation](https://github.com/PaddlePaddle/PaddleOCR/blob/main/doc/doc_en/inference_en.md)
- [DBNet Paper](https://arxiv.org/abs/1911.08947)
- [CRNN Paper](https://arxiv.org/abs/1507.05717)
