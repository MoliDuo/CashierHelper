package pro.xiangyu.cashierhelper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateFeedParserTest {
    private val digest = "a".repeat(64)
    private val url = "${UpdateFeedParser.DOWNLOAD_PREFIX}v1.0.5/CashierHelper-v1.0.5.apk"

    private fun feed(
        versionCode: Int = 1000005,
        sha: String = digest,
        apkUrl: String = url,
        extra: String = "",
    ) = """{"versionName":"1.0.5","versionCode":$versionCode,"apkUrl":"$apkUrl","sha256":"$sha"$extra}"""

    @Test
    fun `a complete feed is read, unknown fields are ignored`() {
        val parsed = UpdateFeedParser.parse(feed(extra = ""","notes":"Fixes","publishedAt":"2026-10-03T08:00:00Z","runId":"7""""))

        assertEquals("1.0.5", parsed?.versionName)
        assertEquals(1000005, parsed?.versionCode)
        assertEquals("Fixes", parsed?.notes)
    }

    @Test
    fun `a feed from before the update feature has no download and is ignored`() {
        assertNull(UpdateFeedParser.parse("""{"versionName":"1.0.2","versionCode":3,"sha":"abc"}"""))
    }

    @Test
    fun `garbage and malformed values are rejected`() {
        assertNull(UpdateFeedParser.parse("not json"))
        assertNull(UpdateFeedParser.parse(feed(versionCode = 0)))
        assertNull(UpdateFeedParser.parse(feed(sha = "abc")))
        assertNull(UpdateFeedParser.parse(feed(sha = "z".repeat(64))))
    }

    @Test
    fun `only downloads from this repository's releases are accepted`() {
        assertNull(UpdateFeedParser.parse(feed(apkUrl = "https://evil.example/app.apk")))
        assertNull(UpdateFeedParser.parse(feed(apkUrl = "http://github.com/MoliDuo/CashierHelper/releases/download/v1/x.apk")))
        assertNull(UpdateFeedParser.parse(feed(apkUrl = "https://github.com/MoliDuo/CashierHelper/releases/latest/download/x.apk")))
        assertNotNull(UpdateFeedParser.parse(feed()))
    }

    @Test
    fun `an entry survives being stored and read back`() {
        val original = UpdateFeedParser.parse(feed(extra = ""","notes":"n""""))!!

        assertEquals(original, UpdateFeedParser.parse(UpdateFeedParser.encode(original)))
    }
}
