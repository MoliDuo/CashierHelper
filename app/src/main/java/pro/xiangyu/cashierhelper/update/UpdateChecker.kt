package pro.xiangyu.cashierhelper.update

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Available(val feed: UpdateFeed) : CheckResult
    data class Failed(val message: String) : CheckResult
}

fun interface UpdateSource {
    suspend fun check(): CheckResult
}

/** Compares the published feed with the installed version. */
class UpdateChecker(
    private val http: OkHttpClient,
    private val installedVersionCode: Int,
    private val feedUrl: String = UpdateFeedParser.FEED_URL,
): UpdateSource {
    override suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(feedUrl).header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext CheckResult.Failed("检查更新失败（${response.code}）")
                val body = response.body?.string().orEmpty()
                val feed = UpdateFeedParser.parse(body)
                    ?: return@withContext CheckResult.Failed("更新信息格式不正确")
                if (feed.versionCode > installedVersionCode) CheckResult.Available(feed) else CheckResult.UpToDate
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            CheckResult.Failed("没有网络，暂时无法检查更新")
        }
    }
}
