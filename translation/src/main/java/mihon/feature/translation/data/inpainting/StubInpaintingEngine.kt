package mihon.feature.translation.data.inpainting

import android.graphics.Bitmap
import mihon.feature.translation.domain.InpaintingEngine

/**
 * Stub implementation for inpainting
 * This will be replaced with LaMa in Phase 4
 */
class StubInpaintingEngine : InpaintingEngine {

    override suspend fun inpaint(
        image: Bitmap,
        masks: List<Bitmap>,
    ): Bitmap {
        // For Phase 1, just return the original image
        // In later phases, this will remove text from the image
        return image.copy(image.config, true)
    }

    override fun getName(): String = "Stub Inpainting (Phase 1)"

    override fun isAvailable(): Boolean = true
}
