package mihon.feature.translation.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Image preprocessing utilities for PaddleOCR
 *
 * Handles image normalization and format conversion for:
 * - DBNet detection model (960×960 input)
 * - CRNN recognition model (48×320 input)
 */
object PaddleOCRPreprocessor {

    // ImageNet normalization parameters
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * Preprocess image for DBNet detection model
     *
     * Steps:
     * 1. Resize to target size (960×960) maintaining aspect ratio
     * 2. Pad with zeros to fill target size
     * 3. Normalize using ImageNet mean/std
     * 4. Convert from HWC (Height, Width, Channel) to CHW format
     *
     * @param bitmap Input image
     * @param targetSize Target size for detection (default 960)
     * @return Pair of (preprocessed FloatArray, original dimensions)
     */
    fun preprocessDetection(
        bitmap: Bitmap,
        targetSize: Int = 960,
    ): Pair<FloatArray, ImageDimensions> {
        // Calculate resize dimensions maintaining aspect ratio
        val (resizeW, resizeH) = calculateResizeShape(
            bitmap.width,
            bitmap.height,
            targetSize,
        )

        // Resize bitmap
        val resized = Bitmap.createScaledBitmap(bitmap, resizeW, resizeH, true)

        // Create output array: CHW format [3, targetSize, targetSize]
        val output = FloatArray(3 * targetSize * targetSize)

        // Fill with zeros (padding)
        output.fill(0f)

        // Convert to CHW and normalize
        for (y in 0 until resizeH) {
            for (x in 0 until resizeW) {
                val pixel = resized.getPixel(x, y)

                // Extract RGB channels [0, 255]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // CHW format: [C, H, W]
                val chOffset = y * targetSize + x
                output[0 * targetSize * targetSize + chOffset] = (r - MEAN[0]) / STD[0]
                output[1 * targetSize * targetSize + chOffset] = (g - MEAN[1]) / STD[1]
                output[2 * targetSize * targetSize + chOffset] = (b - MEAN[2]) / STD[2]
            }
        }

        resized.recycle()

        return Pair(
            output,
            ImageDimensions(
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                resizedWidth = resizeW,
                resizedHeight = resizeH,
                targetSize = targetSize,
            ),
        )
    }

    /**
     * Preprocess image for CRNN recognition model
     *
     * Steps:
     * 1. Resize to height=48, width proportional (max 320)
     * 2. Pad width to 320 if needed
     * 3. Normalize using ImageNet mean/std
     * 4. Convert to CHW format
     *
     * @param bitmap Input image (text line cropped from detection)
     * @param targetHeight Target height (default 48)
     * @param maxWidth Maximum width (default 320)
     * @return Preprocessed FloatArray
     */
    fun preprocessRecognition(
        bitmap: Bitmap,
        targetHeight: Int = 48,
        maxWidth: Int = 320,
    ): FloatArray {
        // Calculate resize width maintaining aspect ratio
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val resizeWidth = min((targetHeight * ratio).toInt(), maxWidth)

        // Resize bitmap
        val resized = Bitmap.createScaledBitmap(bitmap, resizeWidth, targetHeight, true)

        // Create output array: CHW format [3, 48, 320]
        val output = FloatArray(3 * targetHeight * maxWidth)

        // Fill with zeros (padding for width)
        output.fill(0f)

        // Convert to CHW and normalize
        for (y in 0 until targetHeight) {
            for (x in 0 until resizeWidth) {
                val pixel = resized.getPixel(x, y)

                // Extract RGB channels
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // CHW format: [C, H, W]
                val chOffset = y * maxWidth + x
                output[0 * targetHeight * maxWidth + chOffset] = (r - MEAN[0]) / STD[0]
                output[1 * targetHeight * maxWidth + chOffset] = (g - MEAN[1]) / STD[1]
                output[2 * targetHeight * maxWidth + chOffset] = (b - MEAN[2]) / STD[2]
            }
        }

        resized.recycle()

        return output
    }

    /**
     * Calculate resize dimensions maintaining aspect ratio
     *
     * @param width Original width
     * @param height Original height
     * @param targetSize Target size (max dimension)
     * @return Pair of (resized width, resized height)
     */
    private fun calculateResizeShape(
        width: Int,
        height: Int,
        targetSize: Int,
    ): Pair<Int, Int> {
        val ratio = width.toFloat() / height.toFloat()

        return if (width > height) {
            // Landscape: width = targetSize
            val w = min(width, targetSize)
            val h = (w / ratio).toInt()
            Pair(w, h)
        } else {
            // Portrait: height = targetSize
            val h = min(height, targetSize)
            val w = (h * ratio).toInt()
            Pair(w, h)
        }
    }

    /**
     * Enhance image contrast before preprocessing
     * Improves OCR accuracy on low-contrast images
     *
     * @param bitmap Input image
     * @param factor Contrast factor (1.0 = no change, > 1.0 = more contrast)
     * @return Enhanced bitmap
     */
    fun enhanceContrast(bitmap: Bitmap, factor: Float = 1.5f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val enhanced = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)

                // Extract RGB
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
                val a = ((pixel shr 24) and 0xFF)

                // Apply contrast formula: newValue = factor * (oldValue - 128) + 128
                val newR = ((factor * (r - 128) + 128).toInt()).coerceIn(0, 255)
                val newG = ((factor * (g - 128) + 128).toInt()).coerceIn(0, 255)
                val newB = ((factor * (b - 128) + 128).toInt()).coerceIn(0, 255)

                // Combine channels
                val newPixel = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
                enhanced.setPixel(x, y, newPixel)
            }
        }

        return enhanced
    }

    /**
     * Convert image to grayscale (useful for text recognition)
     *
     * @param bitmap Input image
     * @return Grayscale bitmap
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)

                // Extract RGB
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
                val a = ((pixel shr 24) and 0xFF)

                // Calculate grayscale using luminosity method
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // Combine channels
                val newPixel = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
                grayscale.setPixel(x, y, newPixel)
            }
        }

        return grayscale
    }

    /**
     * Data class to store image dimensions during preprocessing
     */
    data class ImageDimensions(
        val originalWidth: Int,
        val originalHeight: Int,
        val resizedWidth: Int,
        val resizedHeight: Int,
        val targetSize: Int,
    ) {
        /**
         * Calculate scale ratio for mapping coordinates back to original image
         */
        fun getScaleRatio(): Pair<Float, Float> {
            val scaleX = originalWidth.toFloat() / resizedWidth.toFloat()
            val scaleY = originalHeight.toFloat() / resizedHeight.toFloat()
            return Pair(scaleX, scaleY)
        }
    }
}
