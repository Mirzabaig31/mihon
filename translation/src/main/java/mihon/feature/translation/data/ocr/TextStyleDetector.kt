package mihon.feature.translation.data.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import mihon.feature.translation.domain.models.TextStyle

/**
 * Detects text orientation (vertical, horizontal, or mixed)
 * for proper text ordering in manga/comics
 */
object TextStyleDetector {

    /**
     * Analyze text blocks to determine predominant text style
     */
    fun detectTextStyle(textBlocks: List<Text.TextBlock>): TextStyle {
        if (textBlocks.isEmpty()) return TextStyle.HORIZONTAL

        val verticalCount = textBlocks.count { isVerticalBlock(it) }
        val horizontalCount = textBlocks.size - verticalCount

        return when {
            verticalCount == 0 -> TextStyle.HORIZONTAL
            horizontalCount == 0 -> TextStyle.VERTICAL
            else -> TextStyle.MIXED
        }
    }

    /**
     * Determine if a text block is vertically oriented
     * Vertical text has height significantly greater than width
     */
    private fun isVerticalBlock(block: Text.TextBlock): Boolean {
        val boundingBox = block.boundingBox ?: return false
        val width = boundingBox.width()
        val height = boundingBox.height()

        // If height is more than 2x width, likely vertical
        return height > width * 2
    }

    /**
     * Calculate aspect ratio (height/width) for a text block
     */
    fun getAspectRatio(boundingBox: Rect): Float {
        val width = boundingBox.width().toFloat()
        val height = boundingBox.height().toFloat()
        return if (width > 0) height / width else 1f
    }
}
