package mihon.feature.translation.data.inpainting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.min

/**
 * Utility for tiling large images for memory-efficient inpainting
 *
 * LaMa model expects 512×512 input, but manga pages are typically 1920×1080 or larger.
 * This class splits large images into overlapping tiles, processes each tile independently,
 * then stitches them back together with feathering to prevent visible seams.
 *
 * Algorithm:
 * 1. Split image into 512×512 tiles with 64px overlap
 * 2. Process each tile independently (parallel when possible)
 * 3. Stitch tiles back with gradient feathering at overlaps
 * 4. Result: seamless inpainted image
 *
 * Memory efficiency:
 * - Only 4MB per tile (512×512 ARGB)
 * - Process 4 tiles in parallel on 4-core CPU
 * - Total working memory: 16MB (vs 30MB for full 1920×1080 image)
 */
object InpaintingTiler {

    /**
     * Represents a single tile with its position in the original image
     */
    data class Tile(
        val image: Bitmap,
        val mask: Bitmap,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun recycle() {
            image.recycle()
            mask.recycle()
        }
    }

    /**
     * Processed tile with its position (for stitching)
     */
    data class ProcessedTile(
        val image: Bitmap,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun recycle() {
            image.recycle()
        }
    }

    /**
     * Split image into tiles for processing
     *
     * @param image Original image to tile
     * @param mask Corresponding mask
     * @param tileSize Size of each tile (default 512 for LaMa)
     * @param overlap Overlap between tiles in pixels (default 64)
     * @return List of tiles with their positions
     */
    fun splitIntoTiles(
        image: Bitmap,
        mask: Bitmap,
        tileSize: Int = 512,
        overlap: Int = 64,
    ): List<Tile> {
        require(image.width == mask.width && image.height == mask.height) {
            "Image and mask must have same dimensions"
        }

        val tiles = mutableListOf<Tile>()
        val stride = tileSize - overlap

        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                // Calculate tile dimensions (may be smaller at edges)
                val tileWidth = min(tileSize, image.width - x)
                val tileHeight = min(tileSize, image.height - y)

                // Extract tile from image and mask
                val tileBitmap = Bitmap.createBitmap(image, x, y, tileWidth, tileHeight)
                val tileMask = Bitmap.createBitmap(mask, x, y, tileWidth, tileHeight)

                // Resize to exact tileSize if needed (for model input)
                val resizedTile = if (tileWidth < tileSize || tileHeight < tileSize) {
                    // Pad to tileSize × tileSize
                    createPaddedTile(tileBitmap, tileMask, tileSize)
                } else {
                    Tile(tileBitmap, tileMask, x, y, tileWidth, tileHeight)
                }

                tiles.add(
                    Tile(
                        image = resizedTile.image,
                        mask = resizedTile.mask,
                        x = x,
                        y = y,
                        width = tileWidth,
                        height = tileHeight,
                    ),
                )

                x += stride
                if (x >= image.width) break
            }
            y += stride
            if (y >= image.height) break
        }

        return tiles
    }

    /**
     * Create a padded tile (for edge tiles smaller than tileSize)
     */
    private fun createPaddedTile(
        image: Bitmap,
        mask: Bitmap,
        targetSize: Int,
    ): Tile {
        val paddedImage = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val paddedMask = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ALPHA_8)

        // Copy original tile to top-left
        val canvas = Canvas(paddedImage)
        canvas.drawBitmap(image, 0f, 0f, null)

        val maskCanvas = Canvas(paddedMask)
        maskCanvas.drawBitmap(mask, 0f, 0f, null)

        // Recycle originals
        image.recycle()
        mask.recycle()

        return Tile(paddedImage, paddedMask, 0, 0, targetSize, targetSize)
    }

    /**
     * Stitch processed tiles back into full image
     *
     * @param tiles List of processed tiles with positions
     * @param targetWidth Width of final image
     * @param targetHeight Height of final image
     * @param overlap Overlap size used during tiling
     * @return Stitched image with feathered seams
     */
    fun stitchTiles(
        tiles: List<ProcessedTile>,
        targetWidth: Int,
        targetHeight: Int,
        overlap: Int = 64,
    ): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Sort tiles by position (top-to-bottom, left-to-right)
        val sortedTiles = tiles.sortedWith(compareBy({ it.y }, { it.x }))

        for (tile in sortedTiles) {
            // Create feathered alpha mask for this tile
            val alphaMask = createFeatherMask(
                tile.width,
                tile.height,
                overlap,
                isLeftEdge = tile.x == 0,
                isTopEdge = tile.y == 0,
                isRightEdge = tile.x + tile.width >= targetWidth,
                isBottomEdge = tile.y + tile.height >= targetHeight,
            )

            // Draw tile with alpha blending
            val maskedTile = applyAlphaMask(tile.image, alphaMask)

            canvas.drawBitmap(maskedTile, tile.x.toFloat(), tile.y.toFloat(), null)

            // Cleanup
            alphaMask.recycle()
            maskedTile.recycle()
        }

        return result
    }

    /**
     * Create feather mask with gradients at edges
     *
     * This creates smooth transitions at tile boundaries to prevent visible seams.
     *
     * @param width Tile width
     * @param height Tile height
     * @param featherSize Size of feather gradient
     * @param isLeftEdge Is this the leftmost tile (no left feather)
     * @param isTopEdge Is this the topmost tile (no top feather)
     * @param isRightEdge Is this the rightmost tile (no right feather)
     * @param isBottomEdge Is this the bottommost tile (no bottom feather)
     * @return Alpha mask bitmap with feathered edges
     */
    private fun createFeatherMask(
        width: Int,
        height: Int,
        featherSize: Int,
        isLeftEdge: Boolean,
        isTopEdge: Boolean,
        isRightEdge: Boolean,
        isBottomEdge: Boolean,
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)

        // Fill with white (full opacity)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint()

        // Left edge gradient (if not left edge tile)
        if (!isLeftEdge && featherSize > 0) {
            val gradient = LinearGradient(
                0f,
                0f,
                featherSize.toFloat(),
                0f,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.WHITE,
                Shader.TileMode.CLAMP,
            )
            paint.shader = gradient
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, 0f, featherSize.toFloat(), height.toFloat(), paint)
        }

        // Top edge gradient (if not top edge tile)
        if (!isTopEdge && featherSize > 0) {
            val gradient = LinearGradient(
                0f,
                0f,
                0f,
                featherSize.toFloat(),
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.WHITE,
                Shader.TileMode.CLAMP,
            )
            paint.shader = gradient
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, 0f, width.toFloat(), featherSize.toFloat(), paint)
        }

        // Right edge gradient (if not right edge tile)
        if (!isRightEdge && featherSize > 0) {
            val gradient = LinearGradient(
                (width - featherSize).toFloat(),
                0f,
                width.toFloat(),
                0f,
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            paint.shader = gradient
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect((width - featherSize).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
        }

        // Bottom edge gradient (if not bottom edge tile)
        if (!isBottomEdge && featherSize > 0) {
            val gradient = LinearGradient(
                0f,
                (height - featherSize).toFloat(),
                0f,
                height.toFloat(),
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            paint.shader = gradient
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, (height - featherSize).toFloat(), width.toFloat(), height.toFloat(), paint)
        }

        return mask
    }

    /**
     * Apply alpha mask to bitmap
     *
     * @param bitmap Source bitmap
     * @param alphaMask Alpha mask (ALPHA_8 format)
     * @return New bitmap with alpha applied
     */
    private fun applyAlphaMask(bitmap: Bitmap, alphaMask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw original bitmap
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Apply alpha mask using DST_IN (keep only where mask is white)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(alphaMask, 0f, 0f, paint)

        return result
    }

    /**
     * Calculate optimal tile count for given image size
     *
     * @param imageWidth Image width
     * @param imageHeight Image height
     * @param tileSize Tile size
     * @param overlap Overlap size
     * @return Pair of (tilesX, tilesY)
     */
    fun calculateTileCount(
        imageWidth: Int,
        imageHeight: Int,
        tileSize: Int = 512,
        overlap: Int = 64,
    ): Pair<Int, Int> {
        val stride = tileSize - overlap

        val tilesX = ((imageWidth - overlap) + stride - 1) / stride
        val tilesY = ((imageHeight - overlap) + stride - 1) / stride

        return Pair(tilesX, tilesY)
    }

    /**
     * Estimate memory usage for tiling
     *
     * @param imageWidth Image width
     * @param imageHeight Image height
     * @param tileSize Tile size
     * @param parallelTiles Number of tiles processed in parallel
     * @return Estimated memory usage in bytes
     */
    fun estimateMemoryUsage(
        imageWidth: Int,
        imageHeight: Int,
        tileSize: Int = 512,
        parallelTiles: Int = 4,
    ): Long {
        // Memory per tile: image (4 bytes/pixel) + mask (1 byte/pixel)
        val bytesPerTile = (tileSize * tileSize * 4) + (tileSize * tileSize)

        // Working memory: parallel tiles × (input + output)
        val workingMemory = parallelTiles * bytesPerTile * 2L

        // Result image memory
        val resultMemory = imageWidth * imageHeight * 4L

        return workingMemory + resultMemory
    }
}
