package mihon.feature.translation.domain

import android.graphics.Bitmap

/**
 * Interface for removing text from images (inpainting)
 */
interface InpaintingEngine {

    /**
     * Remove text from image using provided masks
     * @param image The original image
     * @param masks List of mask bitmaps indicating areas to inpaint
     * @return Image with text removed
     */
    suspend fun inpaint(
        image: Bitmap,
        masks: List<Bitmap>,
    ): Bitmap

    /**
     * Get the name of this inpainting engine
     */
    fun getName(): String

    /**
     * Check if this engine is available
     */
    fun isAvailable(): Boolean
}
