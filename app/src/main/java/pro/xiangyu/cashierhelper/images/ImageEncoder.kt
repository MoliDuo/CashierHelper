package pro.xiangyu.cashierhelper.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ImageTooLargeException(message: String) : Exception(message)

/**
 * Turns any bitmap into a JPEG Cashier accepts. Phone screenshots pass through
 * at full size, the quality ladder keeps ordinary bills well under [targetBytes],
 * and only unusually large or noisy images get scaled down further.
 */
class ImageEncoder(
    private val maxLongEdge: Int = MAX_LONG_EDGE,
    private val targetBytes: Int = TARGET_BYTES,
    private val hardCapBytes: Int = HARD_CAP_BYTES,
) {
    fun encode(source: Bitmap): ByteArray {
        require(source.width > 0 && source.height > 0) { "Image dimensions must be positive" }
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            checkNotNull(source.copy(Bitmap.Config.ARGB_8888, false)) { "Unable to read the image" }
        } else {
            source
        }
        try {
            var scale = minOf(1.0, maxLongEdge.toDouble() / max(source.width, source.height))
            var smallest: ByteArray? = null
            for (round in 0..MAX_SHRINK_ROUNDS) {
                val flattened = flatten(readable, scale)
                try {
                    for (quality in QUALITIES) {
                        val bytes = compress(flattened, quality)
                        if (smallest == null || bytes.size < smallest.size) smallest = bytes
                        if (bytes.size <= targetBytes) return bytes
                    }
                } finally {
                    flattened.recycle()
                }
                scale *= SHRINK_FACTOR
            }
            val best = checkNotNull(smallest)
            if (best.size > hardCapBytes) {
                throw ImageTooLargeException("Image is still ${best.size} bytes after compression")
            }
            return best
        } finally {
            if (readable !== source) readable.recycle()
        }
    }

    /** JPEG has no alpha, so transparent areas would turn black without a white backdrop. */
    private fun flatten(source: Bitmap, scale: Double): Bitmap {
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        return target
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Unable to encode the image as JPEG" }
            output.toByteArray()
        }

    companion object {
        const val MAX_LONG_EDGE = 3200
        const val TARGET_BYTES = 1_572_864
        const val HARD_CAP_BYTES = 4 * 1_048_576
        private val QUALITIES = intArrayOf(85, 75, 65, 55)
        private const val SHRINK_FACTOR = 0.8
        private const val MAX_SHRINK_ROUNDS = 3
    }
}
