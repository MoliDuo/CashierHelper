package pro.xiangyu.cashierhelper.update

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun checker(installed: Int) =
        UpdateChecker(OkHttpClient(), installed, server.url("/feed.json").toString())

    private fun feed(code: Int) =
        """{"versionName":"1.0.5","versionCode":$code,"apkUrl":"${UpdateFeedParser.DOWNLOAD_PREFIX}v1.0.5/x.apk","sha256":"${"b".repeat(64)}"}"""

    @Test
    fun `a newer version is reported`() = runBlocking {
        server.enqueue(MockResponse().setBody(feed(1000005)))

        val result = checker(1000004).check()

        assertEquals(1000005, (result as CheckResult.Available).feed.versionCode)
    }

    @Test
    fun `the same or an older version is up to date`() = runBlocking {
        server.enqueue(MockResponse().setBody(feed(1000004)))
        server.enqueue(MockResponse().setBody(feed(1000003)))

        assertEquals(CheckResult.UpToDate, checker(1000004).check())
        assertEquals(CheckResult.UpToDate, checker(1000004).check())
    }

    @Test
    fun `server errors and broken feeds are failures`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("<html>"))

        assertTrue(checker(1).check() is CheckResult.Failed)
        assertTrue(checker(1).check() is CheckResult.Failed)
    }

    @Test
    fun `no connection is a failure, not a crash`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

        val result = UpdateChecker(client, 1, server.url("/feed.json").toString()).check()

        assertTrue(result is CheckResult.Failed)
    }
}
