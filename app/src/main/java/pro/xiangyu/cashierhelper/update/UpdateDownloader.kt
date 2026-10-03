package pro.xiangyu.cashierhelper.update

import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface Download {
    data class Ready(val file: File) : Download
    data class Failed(val message: String) : Download
}

interface UpdateFiles {
    fun existing(feed: UpdateFeed): File?
    suspend fun download(feed: UpdateFeed): Download
    fun clean(keepVersionCode: Int?)
}

/** Fetches the APK into a cache folder and hands it over only after it passed verification. */
class UpdateDownloader(
    private val http: OkHttpClient,
    private val directory: File,
    private val installed: () -> ApkInfo,
    private val inspector: ApkInspector,
) : UpdateFiles {
    fun fileFor(feed: UpdateFeed) = File(directory, "update-${feed.versionCode}.apk")

    /** A file from an earlier run that still passes verification; used instead of downloading again. */
    override fun existing(feed: UpdateFeed): File? {
        val file = fileFor(feed)
        if (!file.isFile) return null
        return if (UpdateVerifier.verify(file, feed, installed(), inspector) == Verification.Ok) file else null
    }

    override suspend fun download(feed: UpdateFeed): Download = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(feed)
        val partial = File(directory, target.name + ".part")
        try {
            http.newCall(Request.Builder().url(feed.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Download.Failed("下载失败（${response.code}）")
                val body = response.body ?: return@withContext Download.Failed("下载失败")
                if (body.contentLength() > MAX_BYTES) return@withContext Download.Failed("安装包异常大，已放弃下载")
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) return@withContext Download.Failed("安装包异常大，已放弃下载")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            when (val verdict = UpdateVerifier.verify(partial, feed, installed(), inspector)) {
                Verification.Ok -> {
                    target.delete()
                    if (!partial.renameTo(target)) return@withContext Download.Failed("保存安装包失败")
                    Download.Ready(target)
                }
                is Verification.Rejected -> Download.Failed(verdict.reason.message)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Download.Failed("下载中断，或手机存储空间不足")
        } finally {
            partial.delete()
        }
    }

    /** Removes files of versions that are installed or superseded. */
    override fun clean(keepVersionCode: Int?) {
        directory.listFiles()?.forEach { file ->
            if (keepVersionCode == null || file.name != "update-$keepVersionCode.apk") file.delete()
        }
    }

    private companion object {
        const val MAX_BYTES = 120L * 1024 * 1024
    }
}
