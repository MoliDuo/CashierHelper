package pro.xiangyu.cashierhelper.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One entry of the update feed, `release-metadata.json` on the latest GitHub release. */
@Serializable
data class UpdateFeed(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String? = null,
    val notes: String? = null,
)

object UpdateFeedParser {
    /** The feed is the fixed entry point; the APK it names is a file of one specific release. */
    const val FEED_URL = "https://github.com/MoliDuo/CashierHelper/releases/latest/download/release-metadata.json"
    const val DOWNLOAD_PREFIX = "https://github.com/MoliDuo/CashierHelper/releases/download/"

    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun encode(feed: UpdateFeed): String = json.encodeToString(UpdateFeed.serializer(), feed)

    /** Null for anything that is not a well-formed feed, including releases from before the feed existed. */
    fun parse(text: String): UpdateFeed? {
        val feed = try {
            json.decodeFromString<UpdateFeed>(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (feed.versionCode <= 0 || feed.versionName.isBlank()) return null
        if (!sha256Pattern.matches(feed.sha256)) return null
        if (!feed.apkUrl.startsWith(DOWNLOAD_PREFIX)) return null
        return feed
    }
}
