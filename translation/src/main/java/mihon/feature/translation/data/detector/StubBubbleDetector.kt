package mihon.feature.translation.data.detector

import android.graphics.Bitmap
import android.graphics.RectF
import mihon.feature.translation.domain.BubbleDetector
import mihon.feature.translation.domain.models.BubbleType
import mihon.feature.translation.domain.models.DetectionResult
import mihon.feature.translation.domain.models.SpeechBubble
import mihon.feature.translation.domain.models.TextStyle

/**
 * Stub implementation for bubble detection
 * This will be replaced with YOLOv10 in Phase 3
 */
class StubBubbleDetector : BubbleDetector {

    override suspend fun detectBubbles(image: Bitmap): DetectionResult {
        // For now, return a simple full-page detection
        // This allows us to test the translation pipeline without ML models
        val fullPageBubble = SpeechBubble(
            boundingBox = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            confidence = 1.0f,
            type = BubbleType.SPEECH,
            textStyle = TextStyle.HORIZONTAL,
        )

        return DetectionResult(
            bubbles = listOf(fullPageBubble),
            textRegions = listOf(fullPageBubble.boundingBox),
            masks = emptyList(),
            metadata = mapOf("detector" to "stub"),
        )
    }

    override fun getName(): String = "Stub Detector (Phase 1)"

    override fun isAvailable(): Boolean = true
}
