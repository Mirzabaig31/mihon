package mihon.feature.translation.data.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import mihon.feature.translation.domain.models.TextBlock
import mihon.feature.translation.domain.models.TextStyle
import kotlin.math.abs

/**
 * Processes and sorts text blocks into correct reading order
 * Handles vertical (manga), horizontal, and mixed text layouts
 */
class TextBlockProcessor {

    /**
     * Process ML Kit text blocks and convert to domain model
     * with correct reading order
     */
    fun processTextBlocks(
        visionTextBlocks: List<Text.TextBlock>,
        textStyle: TextStyle,
    ): List<TextBlock> {
        if (visionTextBlocks.isEmpty()) return emptyList()

        return when (textStyle) {
            TextStyle.VERTICAL -> processVerticalText(visionTextBlocks)
            TextStyle.HORIZONTAL -> processHorizontalText(visionTextBlocks)
            TextStyle.MIXED -> processMixedText(visionTextBlocks)
        }
    }

    /**
     * Process vertical text (typical manga layout)
     * Reading order: Right to left, top to bottom
     */
    private fun processVerticalText(blocks: List<Text.TextBlock>): List<TextBlock> {
        // Group blocks into vertical columns based on X-coordinate proximity
        val columns = groupIntoVerticalColumns(blocks)

        // Sort columns right-to-left (manga reading order)
        val sortedColumns = columns.sortedByDescending { column ->
            column.map { it.boundingBox?.centerX() ?: 0 }.average()
        }

        // Within each column, sort top-to-bottom
        return sortedColumns.flatMap { column ->
            column.sortedBy { it.boundingBox?.top ?: 0 }
                .map { it.toTextBlock() }
        }
    }

    /**
     * Process horizontal text (standard left-to-right layout)
     * Reading order: Top to bottom, left to right
     */
    private fun processHorizontalText(blocks: List<Text.TextBlock>): List<TextBlock> {
        // Group blocks into horizontal lines based on Y-coordinate proximity
        val lines = groupIntoHorizontalLines(blocks)

        // Sort lines top-to-bottom
        val sortedLines = lines.sortedBy { line ->
            line.map { it.boundingBox?.top ?: 0 }.average()
        }

        // Within each line, sort left-to-right
        return sortedLines.flatMap { line ->
            line.sortedBy { it.boundingBox?.left ?: 0 }
                .map { it.toTextBlock() }
        }
    }

    /**
     * Process mixed text (combination of vertical and horizontal)
     * Separate vertical and horizontal blocks, then process each group
     */
    private fun processMixedText(blocks: List<Text.TextBlock>): List<TextBlock> {
        val (verticalBlocks, horizontalBlocks) = blocks.partition { block ->
            val boundingBox = block.boundingBox ?: return@partition false
            TextStyleDetector.getAspectRatio(boundingBox) > 1.5f
        }

        val processedVertical = processVerticalText(verticalBlocks)
        val processedHorizontal = processHorizontalText(horizontalBlocks)

        // In manga, vertical text typically comes first (dialogue)
        return processedVertical + processedHorizontal
    }

    /**
     * Group text blocks into vertical columns
     * Blocks within ~50px horizontally belong to the same column
     */
    private fun groupIntoVerticalColumns(blocks: List<Text.TextBlock>): List<List<Text.TextBlock>> {
        if (blocks.isEmpty()) return emptyList()

        val columns = mutableListOf<MutableList<Text.TextBlock>>()
        val sorted = blocks.sortedBy { it.boundingBox?.centerX() ?: 0 }

        sorted.forEach { block ->
            val blockX = block.boundingBox?.centerX() ?: 0
            val matchingColumn = columns.find { column ->
                val columnX = column.first().boundingBox?.centerX() ?: 0
                abs(blockX - columnX) < COLUMN_PROXIMITY_THRESHOLD
            }

            if (matchingColumn != null) {
                matchingColumn.add(block)
            } else {
                columns.add(mutableListOf(block))
            }
        }

        return columns
    }

    /**
     * Group text blocks into horizontal lines
     * Blocks within ~30px vertically belong to the same line
     */
    private fun groupIntoHorizontalLines(blocks: List<Text.TextBlock>): List<List<Text.TextBlock>> {
        if (blocks.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableList<Text.TextBlock>>()
        val sorted = blocks.sortedBy { it.boundingBox?.centerY() ?: 0 }

        sorted.forEach { block ->
            val blockY = block.boundingBox?.centerY() ?: 0
            val matchingLine = lines.find { line ->
                val lineY = line.first().boundingBox?.centerY() ?: 0
                abs(blockY - lineY) < LINE_PROXIMITY_THRESHOLD
            }

            if (matchingLine != null) {
                matchingLine.add(block)
            } else {
                lines.add(mutableListOf(block))
            }
        }

        return lines
    }

    /**
     * Convert ML Kit Text.TextBlock to domain TextBlock model
     */
    private fun Text.TextBlock.toTextBlock(): TextBlock {
        val boundingBox = this.boundingBox ?: Rect()
        return TextBlock(
            text = this.text,
            boundingBox = android.graphics.RectF(
                boundingBox.left.toFloat(),
                boundingBox.top.toFloat(),
                boundingBox.right.toFloat(),
                boundingBox.bottom.toFloat(),
            ),
            confidence = this.lines.map { it.confidence ?: 0f }.average().toFloat(),
        )
    }

    companion object {
        private const val COLUMN_PROXIMITY_THRESHOLD = 50 // pixels
        private const val LINE_PROXIMITY_THRESHOLD = 30 // pixels
    }
}
