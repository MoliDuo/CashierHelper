package pro.xiangyu.cashierhelper.update

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import pro.xiangyu.cashierhelper.config.AppPrefs

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Downloading(val feed: UpdateFeed) : UpdateStatus
    data class Ready(val feed: UpdateFeed, val file: File) : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

enum class InstallStart { STARTED, NEEDS_PERMISSION, FAILED, NOTHING_READY }

fun interface UpdateInstaller {
    fun start(file: File): InstallStart
}

interface UpdateNotifier {
    fun updateReady(feed: UpdateFeed)
    fun clearUpdate()
}

/**
 * Finds a newer version, downloads and verifies it in the background, and offers it.
 * Background checks stay quiet unless an update is ready; only a manual check reports
 * "up to date" or a failure.
 */
class UpdateManager(
    val enabled: Boolean,
    private val installedVersionCode: Int,
    private val source: UpdateSource,
    private val files: UpdateFiles,
    private val installer: UpdateInstaller,
    private val prefs: AppPrefs,
    private val notifier: UpdateNotifier,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutableStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = mutableStatus.asStateFlow()

    private val mutableDeferred = MutableStateFlow(prefs.deferredUpdateCode)
    val deferred: StateFlow<Int> = mutableDeferred.asStateFlow()

    private val running = Mutex()

    /** After a restart: offer a downloaded update again, or clean up after one that was installed. */
    fun restore() {
        if (!enabled) return
        val feed = prefs.pendingUpdate?.let(UpdateFeedParser::parse)
        if (feed == null || feed.versionCode <= installedVersionCode) {
            prefs.pendingUpdate = null
            files.clean(null)
            notifier.clearUpdate()
            return
        }
        val file = files.existing(feed)
        if (file == null) {
            files.clean(null)
            return
        }
        mutableStatus.value = UpdateStatus.Ready(feed, file)
    }

    /** Opening the app counts as a check, but not more than once in a while. */
    suspend fun checkOnOpen() {
        if (now() - prefs.lastUpdateCheck >= OPEN_CHECK_INTERVAL_MILLIS) check(manual = false)
    }

    suspend fun check(manual: Boolean) {
        if (!enabled) return
        if (!running.tryLock()) return
        try {
            prefs.lastUpdateCheck = now()
            val before = mutableStatus.value
            if (manual) mutableStatus.value = UpdateStatus.Checking
            when (val result = source.check()) {
                CheckResult.UpToDate -> {
                    prefs.pendingUpdate = null
                    files.clean(null)
                    notifier.clearUpdate()
                    mutableStatus.value = if (manual) UpdateStatus.UpToDate else UpdateStatus.Idle
                }
                is CheckResult.Failed -> mutableStatus.value = when {
                    manual -> UpdateStatus.Failed(result.message)
                    before is UpdateStatus.Ready -> before
                    else -> UpdateStatus.Idle
                }
                is CheckResult.Available -> offer(result.feed, manual)
            }
        } finally {
            running.unlock()
        }
    }

    private suspend fun offer(feed: UpdateFeed, manual: Boolean) {
        val file = files.existing(feed) ?: run {
            mutableStatus.value = UpdateStatus.Downloading(feed)
            when (val download = files.download(feed)) {
                is Download.Ready -> download.file
                is Download.Failed -> {
                    mutableStatus.value = UpdateStatus.Failed(download.message)
                    return
                }
            }
        }
        files.clean(feed.versionCode)
        prefs.pendingUpdate = UpdateFeedParser.encode(feed)
        mutableStatus.value = UpdateStatus.Ready(feed, file)
        if (manual) defer(0)
        if (prefs.notifiedUpdateCode != feed.versionCode) {
            prefs.notifiedUpdateCode = feed.versionCode
            notifier.updateReady(feed)
        }
    }

    /** "稍后": the banner and notification go away until a newer version is found. */
    fun defer() {
        val ready = mutableStatus.value as? UpdateStatus.Ready ?: return
        defer(ready.feed.versionCode)
        notifier.clearUpdate()
    }

    private fun defer(versionCode: Int) {
        prefs.deferredUpdateCode = versionCode
        mutableDeferred.value = versionCode
    }

    fun install(): InstallStart {
        val ready = mutableStatus.value as? UpdateStatus.Ready ?: return InstallStart.NOTHING_READY
        return installer.start(ready.file)
    }

    companion object {
        const val OPEN_CHECK_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
