package mihon.feature.translation.domain

import android.graphics.Bitmap
import mihon.feature.translation.domain.models.DetectionResult

/**
 * Interface for detecting speech bubbles in comic pages
 */
interface BubbleDetector {

    /**
     * Detect speech bubbles in a comic page
     * @param image The comic page image
     * @return Detection result containing bubbles, regions, and masks
     */
    suspend fun detectBubbles(image: Bitmap): DetectionResult

    /**
     * Get the name of this detector implementation
     */
    fun getName(): String

    /**
     * Check if this detector is available (e.g., model is loaded)
     */
    fun isAvailable(): Boolean
}
