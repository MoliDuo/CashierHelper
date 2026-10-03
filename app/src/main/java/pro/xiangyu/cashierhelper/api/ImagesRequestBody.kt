package pro.xiangyu.cashierhelper.api

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * `{"images":[{"data":"<base64>","mimeType":"image/jpeg"}, ...],"entryDate":"..."}`
 * streamed straight from the files, so a few megabytes of Base64 never sit in
 * memory as one string. The body can be written again, which lets OkHttp
 * resend it after a dropped connection.
 */
internal class ImagesRequestBody(
    private val files: List<File>,
    private val entryDate: String,
) : RequestBody() {
    private val tail = """],"entryDate":${jsonString(entryDate)}}"""

    override fun contentType() = JSON

    override fun contentLength(): Long {
        var length = HEAD.length.toLong() + tail.length
        files.forEachIndexed { index, file ->
            if (index > 0) length += 1
            length += ITEM_PREFIX.length + base64Length(file.length()) + ITEM_SUFFIX.length
        }
        return length
    }

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8(HEAD)
        files.forEachIndexed { index, file ->
            if (index > 0) sink.writeUtf8(",")
            sink.writeUtf8(ITEM_PREFIX)
            writeBase64(file, sink)
            sink.writeUtf8(ITEM_SUFFIX)
        }
        sink.writeUtf8(tail)
    }

    private fun writeBase64(file: File, sink: BufferedSink) {
        // Closing the Base64 stream writes the final padding, but it would also close the sink.
        val target = object : FilterOutputStream(sink.outputStream()) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() = flush()
        }
        val encoder: OutputStream = Base64.getEncoder().wrap(target)
        file.inputStream().use { input -> input.copyTo(encoder) }
        encoder.close()
    }

    private fun base64Length(bytes: Long): Long = (bytes + 2) / 3 * 4

    private companion object {
        val JSON = "application/json".toMediaType()
        const val HEAD = """{"images":["""
        const val ITEM_PREFIX = """{"data":""""
        const val ITEM_SUFFIX = """","mimeType":"image/jpeg"}"""

        // entryDate is validated as YYYY-MM-DD by the caller; escaping keeps the body valid regardless.
        fun jsonString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
