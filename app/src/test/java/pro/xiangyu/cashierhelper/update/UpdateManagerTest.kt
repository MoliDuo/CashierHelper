package pro.xiangyu.cashierhelper.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pro.xiangyu.cashierhelper.config.AppPrefs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class FakeFiles(private val directory: File) : UpdateFiles {
        var downloads = 0
        var failure: String? = null
        val cleaned = mutableListOf<Int?>()

        private fun fileFor(feed: UpdateFeed) = File(directory, "u-${feed.versionCode}.apk")

        override fun existing(feed: UpdateFeed): File? = fileFor(feed).takeIf { it.isFile }

        override suspend fun download(feed: UpdateFeed): Download {
            downloads++
            failure?.let { return Download.Failed(it) }
            return Download.Ready(fileFor(feed).also { it.writeText("apk") })
        }

        override fun clean(keepVersionCode: Int?) {
            cleaned += keepVersionCode
            directory.listFiles()?.forEach { if (keepVersionCode == null || it.name != "u-$keepVersionCode.apk") it.delete() }
        }
    }

    private class FakeNotifier : UpdateNotifier {
        val offered = mutableListOf<UpdateFeed>()
        var cleared = 0

        override fun updateReady(feed: UpdateFeed) {
            offered += feed
        }

        override fun clearUpdate() {
            cleared++
        }
    }

    private var result: CheckResult = CheckResult.UpToDate
    private var installStart = InstallStart.STARTED
    private val installedFiles = mutableListOf<File>()
    private var clock = 1_000_000_000L
    private val notifier = FakeNotifier()
    private lateinit var files: FakeFiles
    private lateinit var prefs: AppPrefs

    private fun feed(code: Int = 1000005) = UpdateFeed(
        versionName = "1.0.${code - 1000000}",
        versionCode = code,
        apkUrl = "${UpdateFeedParser.DOWNLOAD_PREFIX}v1.0.5/x.apk",
        sha256 = "c".repeat(64),
        notes = "Notes",
    )

    @Before
    fun setUp() {
        files = FakeFiles(temp.newFolder("updates"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPrefs(context.getSharedPreferences("test-${System.nanoTime()}", Context.MODE_PRIVATE))
    }

    private fun manager(enabled: Boolean = true) = UpdateManager(
        enabled = enabled,
        installedVersionCode = 1000004,
        source = { result },
        files = files,
        installer = { file ->
            installedFiles += file
            installStart
        },
        prefs = prefs,
        notifier = notifier,
        now = { clock },
    )

    @Test
    fun `a new version is downloaded and offered once`() = runBlocking {
        result = CheckResult.Available(feed())
        val manager = manager()

        manager.check(manual = false)
        manager.check(manual = false)

        assertTrue(manager.status.value is UpdateStatus.Ready)
        assertEquals(1, files.downloads)
        assertEquals(listOf(feed()), notifier.offered)
    }

    @Test
    fun `a quiet check without news shows nothing, a manual one says it is up to date`() = runBlocking {
        val manager = manager()

        manager.check(manual = false)
        assertEquals(UpdateStatus.Idle, manager.status.value)

        manager.check(manual = true)
        assertEquals(UpdateStatus.UpToDate, manager.status.value)
    }

    @Test
    fun `a failed quiet check is silent, a failed manual check says why`() = runBlocking {
        result = CheckResult.Failed("没有网络")
        val manager = manager()

        manager.check(manual = false)
        assertEquals(UpdateStatus.Idle, manager.status.value)

        manager.check(manual = true)
        assertEquals(UpdateStatus.Failed("没有网络"), manager.status.value)
    }

    @Test
    fun `a failed quiet check does not hide an update that is already ready`() = runBlocking {
        result = CheckResult.Available(feed())
        val manager = manager()
        manager.check(manual = false)

        result = CheckResult.Failed("没有网络")
        manager.check(manual = false)

        assertTrue(manager.status.value is UpdateStatus.Ready)
    }

    @Test
    fun `a rejected download is reported and nothing is offered`() = runBlocking {
        result = CheckResult.Available(feed())
        files.failure = "下载的安装包签名与当前版本不一致，已丢弃"
        val manager = manager()

        manager.check(manual = false)

        assertEquals(UpdateStatus.Failed(files.failure!!), manager.status.value)
        assertTrue(notifier.offered.isEmpty())
        assertNull(prefs.pendingUpdate)
    }

    @Test
    fun `later hides the update until a newer one appears, a manual check brings it back`() = runBlocking {
        result = CheckResult.Available(feed())
        val manager = manager()
        manager.check(manual = false)

        manager.defer()
        assertEquals(1000005, manager.deferred.value)

        result = CheckResult.Available(feed(1000006))
        manager.check(manual = false)
        assertEquals(1000005, manager.deferred.value) // 1000006 is not deferred
        assertEquals(listOf(feed(), feed(1000006)), notifier.offered)

        manager.defer()
        manager.check(manual = true)
        assertEquals(0, manager.deferred.value)
    }

    @Test
    fun `opening the app checks at most once in ten minutes`() = runBlocking {
        result = CheckResult.Available(feed())
        val manager = manager()

        manager.checkOnOpen()
        files.downloads = 0
        clock += 5 * 60_000
        manager.checkOnOpen()
        assertEquals(0, files.downloads)

        clock += 6 * 60_000
        result = CheckResult.UpToDate
        manager.checkOnOpen()
        assertEquals(UpdateStatus.Idle, manager.status.value)
    }

    @Test
    fun `install passes the verified file to the installer`() = runBlocking {
        val manager = manager()
        assertEquals(InstallStart.NOTHING_READY, manager.install())

        result = CheckResult.Available(feed())
        manager.check(manual = false)
        installStart = InstallStart.NEEDS_PERMISSION

        assertEquals(InstallStart.NEEDS_PERMISSION, manager.install())
        assertEquals(1, installedFiles.size)
    }

    @Test
    fun `after a restart a downloaded update is offered again`() = runBlocking {
        result = CheckResult.Available(feed())
        manager().check(manual = false)

        val restarted = manager()
        restarted.restore()

        assertEquals(feed(), (restarted.status.value as UpdateStatus.Ready).feed)
    }

    @Test
    fun `after the update was installed its leftovers are removed`() = runBlocking {
        result = CheckResult.Available(feed(1000004).copy(versionCode = 1000004))
        prefs.pendingUpdate = UpdateFeedParser.encode(feed(1000004))
        File(temp.root, "updates/u-1000004.apk").writeText("old")

        val restarted = manager()
        restarted.restore()

        assertEquals(UpdateStatus.Idle, restarted.status.value)
        assertNull(prefs.pendingUpdate)
        assertTrue(notifier.cleared > 0)
        assertTrue(files.cleaned.contains(null))
    }

    @Test
    fun `a disabled manager does nothing`() = runBlocking {
        result = CheckResult.Available(feed())
        val manager = manager(enabled = false)

        manager.check(manual = true)
        manager.restore()

        assertEquals(UpdateStatus.Idle, manager.status.value)
        assertEquals(0, files.downloads)
        assertFalse(manager.enabled)
    }
}
