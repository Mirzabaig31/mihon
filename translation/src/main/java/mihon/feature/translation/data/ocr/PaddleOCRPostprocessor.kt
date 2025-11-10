package mihon.feature.translation.data.ocr

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Post-processing utilities for PaddleOCR
 *
 * Handles:
 * - DBNet detection output processing (probability map → text boxes)
 * - CTC recognition decoding (probabilities → text)
 * - Non-maximum suppression for overlapping boxes
 */
object PaddleOCRPostprocessor {

    /**
     * Process DBNet detection output to extract text boxes
     *
     * DBNet outputs a probability map [1, 960, 960] where each pixel represents
     * the probability of being part of text.
     *
     * Algorithm:
     * 1. Threshold probability map (> 0.3 = text)
     * 2. Find contours in binary map
     * 3. Convert contours to rotated rectangles
     * 4. Filter by size and confidence
     * 5. Apply NMS to remove overlapping boxes
     *
     * @param probabilityMap Model output [H, W] with probabilities
     * @param dimensions Original image dimensions
     * @param threshold Detection threshold (default 0.3)
     * @param boxThreshold Minimum box score (default 0.5)
     * @return List of detected text boxes
     */
    fun processDetection(
        probabilityMap: FloatArray,
        dimensions: PaddleOCRPreprocessor.ImageDimensions,
        threshold: Float = 0.3f,
        boxThreshold: Float = 0.5f,
    ): List<TextBox> {
        val height = dimensions.targetSize
        val width = dimensions.targetSize

        // Convert probability map to binary mask
        val binaryMask = BooleanArray(height * width) { i ->
            probabilityMap[i] > threshold
        }

        // Find connected components (simplified contour detection)
        val boxes = mutableListOf<TextBox>()
        val visited = BooleanArray(height * width) { false }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (binaryMask[idx] && !visited[idx]) {
                    // Found new component, flood fill to get all connected pixels
                    val component = floodFill(binaryMask, visited, x, y, width, height)

                    if (component.size > 10) { // Minimum size filter
                        // Calculate bounding box
                        val box = calculateBoundingBox(component, dimensions)

                        // Calculate confidence (average probability in box)
                        val confidence = calculateBoxConfidence(probabilityMap, component, width)

                        if (confidence > boxThreshold) {
                            boxes.add(box.copy(confidence = confidence))
                        }
                    }
                }
            }
        }

        // Apply non-maximum suppression
        return applyNMS(boxes, iouThreshold = 0.3f)
    }

    /**
     * Decode CTC recognition output to text
     *
     * CTC (Connectionist Temporal Classification) outputs a sequence of character
     * probabilities. We need to:
     * 1. Find most likely character at each timestep
     * 2. Remove duplicates
     * 3. Remove blank tokens
     * 4. Map indices to characters using dictionary
     *
     * @param probabilities Model output [sequence_length, num_classes]
     * @param dictionary Character dictionary (index → character)
     * @param blankIndex Index of blank token (usually 0)
     * @return Decoded text and confidence
     */
    fun decodeCTC(
        probabilities: Array<FloatArray>,
        dictionary: List<String>,
        blankIndex: Int = 0,
    ): Pair<String, Float> {
        val sequenceLength = probabilities.size
        val numClasses = probabilities[0].size

        // Greedy decoding: pick most likely character at each timestep
        val decoded = mutableListOf<Int>()
        var totalConfidence = 0f
        var prevChar = blankIndex

        for (t in 0 until sequenceLength) {
            // Find character with highest probability
            var maxProb = 0f
            var maxIndex = blankIndex

            for (c in 0 until numClasses) {
                if (probabilities[t][c] > maxProb) {
                    maxProb = probabilities[t][c]
                    maxIndex = c
                }
            }

            // Add to decoded sequence (skip blanks and duplicates)
            if (maxIndex != blankIndex && maxIndex != prevChar) {
                decoded.add(maxIndex)
                totalConfidence += maxProb
            }

            prevChar = maxIndex
        }

        // Convert indices to text
        val text = decoded.joinToString("") { index ->
            if (index < dictionary.size) dictionary[index] else ""
        }

        val confidence = if (decoded.isNotEmpty()) {
            totalConfidence / decoded.size
        } else {
            0f
        }

        return Pair(text, confidence)
    }

    /**
     * Flood fill algorithm to find connected components
     */
    private fun floodFill(
        mask: BooleanArray,
        visited: BooleanArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
    ): List<PointF> {
        val points = mutableListOf<PointF>()
        val stack = mutableListOf(Pair(startX, startY))

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)

            if (x < 0 || x >= width || y < 0 || y >= height) continue

            val idx = y * width + x
            if (visited[idx] || !mask[idx]) continue

            visited[idx] = true
            points.add(PointF(x.toFloat(), y.toFloat()))

            // Add neighbors
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }

        return points
    }

    /**
     * Calculate bounding box from points
     */
    private fun calculateBoundingBox(
        points: List<PointF>,
        dimensions: PaddleOCRPreprocessor.ImageDimensions,
    ): TextBox {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (point in points) {
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }

        // Scale back to original image coordinates
        val (scaleX, scaleY) = dimensions.getScaleRatio()

        return TextBox(
            boundingBox = RectF(
                minX * scaleX,
                minY * scaleY,
                maxX * scaleX,
                maxY * scaleY,
            ),
            confidence = 0f, // Will be calculated separately
            points = points.map { PointF(it.x * scaleX, it.y * scaleY) },
        )
    }

    /**
     * Calculate average confidence in bounding box
     */
    private fun calculateBoxConfidence(
        probabilityMap: FloatArray,
        points: List<PointF>,
        width: Int,
    ): Float {
        var sum = 0f
        for (point in points) {
            val idx = point.y.toInt() * width + point.x.toInt()
            sum += probabilityMap[idx]
        }
        return sum / points.size
    }

    /**
     * Apply Non-Maximum Suppression to remove overlapping boxes
     */
    private fun applyNMS(boxes: List<TextBox>, iouThreshold: Float): List<TextBox> {
        if (boxes.isEmpty()) return emptyList()

        // Sort by confidence (descending)
        val sorted = boxes.sortedByDescending { it.confidence }
        val keep = mutableListOf<TextBox>()

        for (box in sorted) {
            var shouldKeep = true

            for (kept in keep) {
                val iou = calculateIoU(box.boundingBox, kept.boundingBox)
                if (iou > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }

            if (shouldKeep) {
                keep.add(box)
            }
        }

        return keep
    }

    /**
     * Calculate Intersection over Union (IoU) for two rectangles
     */
    private fun calculateIoU(rect1: RectF, rect2: RectF): Float {
        val intersectLeft = max(rect1.left, rect2.left)
        val intersectTop = max(rect1.top, rect2.top)
        val intersectRight = min(rect1.right, rect2.right)
        val intersectBottom = min(rect1.bottom, rect2.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f // No intersection
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val rect1Area = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
        val rect2Area = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)
        val unionArea = rect1Area + rect2Area - intersectArea

        return intersectArea / unionArea
    }

    /**
     * Data class for detected text boxes
     */
    data class TextBox(
        val boundingBox: RectF,
        val confidence: Float,
        val points: List<PointF>, // Original contour points
    )
}
