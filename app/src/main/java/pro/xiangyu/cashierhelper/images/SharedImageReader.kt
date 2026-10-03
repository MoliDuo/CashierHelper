package pro.xiangyu.cashierhelper.images

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An image ready to become part of a task, with the time it was taken when that is known. */
class LoadedImage(val jpeg: ByteArray, val takenAtMillis: Long?)

fun interface SharedImageLoader {
    /** Null when the image cannot be read or encoded. */
    suspend fun load(uri: Uri): LoadedImage?
}

/**
 * Reads images shared from other apps or picked from the gallery. The system
 * decoder handles HEIC, WebP and PNG and applies the EXIF rotation.
 */
class SharedImageReader(
    private val resolver: ContentResolver,
    private val encoder: ImageEncoder,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : SharedImageLoader {
    override suspend fun load(uri: Uri): LoadedImage? = withContext(Dispatchers.IO) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > ImageEncoder.MAX_LONG_EDGE) {
                    val scale = ImageEncoder.MAX_LONG_EDGE.toDouble() / longEdge
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).roundToInt()),
                        max(1, (info.size.height * scale).roundToInt()),
                    )
                }
            }
            try {
                LoadedImage(encoder.encode(bitmap), takenAt(uri))
            } finally {
                bitmap.recycle()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun takenAt(uri: Uri): Long? = fromMediaStore(uri) ?: fromExif(uri)

    private fun fromMediaStore(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun fromExif(uri: Uri): Long? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val value = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            EntryDates.parseExifTimestamp(value, zone())
        }
    }.getOrNull()
}
