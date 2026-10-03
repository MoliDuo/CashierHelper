package pro.xiangyu.cashierhelper.ui

import android.app.Application
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.xiangyu.cashierhelper.CashierHelperApplication
import pro.xiangyu.cashierhelper.api.ConnectionTest
import pro.xiangyu.cashierhelper.capture.CashierAccessibilityService
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.ApiKeyValidator
import pro.xiangyu.cashierhelper.config.BaseUrlValidator
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.notify.NotificationAvailability
import android.widget.Toast
import pro.xiangyu.cashierhelper.R
import pro.xiangyu.cashierhelper.tasks.SharedImageSubmitter
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskSource
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskState
import pro.xiangyu.cashierhelper.ui.home.HomeStatus
import pro.xiangyu.cashierhelper.update.InstallStart
import pro.xiangyu.cashierhelper.update.UpdateFeed
import pro.xiangyu.cashierhelper.update.UpdateStatus
import pro.xiangyu.cashierhelper.ui.home.HomeStatusCalculator
import pro.xiangyu.cashierhelper.ui.onboarding.OnboardingFlow
import pro.xiangyu.cashierhelper.ui.onboarding.Signals
import pro.xiangyu.cashierhelper.ui.onboarding.Step

enum class Screen { ONBOARDING, HOME, SETTINGS }

/** Result of pressing "测试并保存". */
sealed interface SaveState {
    data object Idle : SaveState
    data object Testing : SaveState
    data object Saved : SaveState

    /** [canForce]: the server could not be reached, so saving without a test is offered. */
    data class Failed(val message: String, val canForce: Boolean = false) : SaveState
}

data class MainUiState(
    val config: ConfigState? = null,
    val service: ServiceState = ServiceState.OFF,
    val notifications: NotificationAvailability.Status =
        NotificationAvailability.Status(permissionGranted = true, appEnabled = true, channelEnabled = true),
    val batteryUnrestricted: Boolean = true,
    val tasks: List<TaskRecord> = emptyList(),
    val launchReason: LaunchReason? = null,
    val screen: Screen = Screen.HOME,
    val save: SaveState = SaveState.Idle,
    val onboardingStep: Step = Step.WELCOME,
    val sideKeyWorked: Boolean = false,
    /** Images chosen in the gallery that wait for the "same bill or separate" answer. */
    val pickedUris: List<Uri> = emptyList(),
    val update: UpdateStatus = UpdateStatus.Idle,
    val updateDeferredCode: Int = 0,
    val updatesEnabled: Boolean = false,
) {
    /** A downloaded update to offer on the home screen; hidden after "稍后" until a newer one appears. */
    val offeredUpdate: UpdateFeed?
        get() = (update as? UpdateStatus.Ready)?.feed?.takeIf { it.versionCode != updateDeferredCode }

    val signals: Signals
        get() = Signals(
            connected = config is ConfigState.Ready,
            serviceRunning = service == ServiceState.RUNNING,
            notificationsVisible = notifications.isVisible,
            batteryUnrestricted = batteryUnrestricted,
            sideKeyWorked = sideKeyWorked,
        )

    val status: HomeStatus
        get() = HomeStatusCalculator.compute(
            config = config,
            service = service,
            keyRejected = tasks.any { it.state == TaskState.NEEDS_ACTION && it.problem == TaskProblem.UNAUTHORIZED },
            notifications = notifications,
            batteryUnrestricted = batteryUnrestricted,
        )

    val needsAction: List<TaskRecord> get() = tasks.filter { it.state == TaskState.NEEDS_ACTION }
    val inProgress: List<TaskRecord>
        get() = tasks.filter { it.state != TaskState.NEEDS_ACTION && it.state != TaskState.DONE }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as CashierHelperApplication).graph
    private val mutableState = MutableStateFlow(
        MainUiState(screen = if (graph.prefs.onboardingDone) Screen.HOME else Screen.ONBOARDING),
    )
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            graph.config.current()
            graph.config.state.collect { config -> update { it.copy(config = config) } }
        }
        viewModelScope.launch {
            graph.tasks.tasks.collect { all ->
                mutableState.update { state ->
                    state.copy(tasks = all.filter { it.state != TaskState.DONE }.sortedBy { it.createdAt })
                }
            }
        }
        viewModelScope.launch {
            CashierAccessibilityService.running.collect { refreshSystem() }
        }
        viewModelScope.launch {
            graph.trial.succeeded.collect { worked -> update { it.copy(sideKeyWorked = worked) } }
        }
        viewModelScope.launch {
            // The test capture is only wanted while the guide is waiting for the side key.
            state.map { it.screen == Screen.ONBOARDING && it.onboardingStep == Step.SIDE_KEY }
                .distinctUntilChanged()
                .collect { waiting -> if (waiting) graph.trial.arm() else graph.trial.disarm() }
        }
        viewModelScope.launch {
            graph.updates.status.collect { status -> update { it.copy(update = status) } }
        }
        viewModelScope.launch {
            graph.updates.deferred.collect { code -> update { it.copy(updateDeferredCode = code) } }
        }
        update { it.copy(updatesEnabled = graph.updates.enabled) }
        viewModelScope.launch {
            graph.updates.restore()
            graph.updates.checkOnOpen()
        }
        refreshSystem()
    }

    /** Every change goes through here so the guide moves on by itself when a step turns out to be done. */
    private fun update(change: (MainUiState) -> MainUiState) {
        mutableState.update { current ->
            val next = change(current)
            next.copy(onboardingStep = OnboardingFlow.settle(next.onboardingStep, next.signals))
        }
    }

    val imageFiles get() = graph.images

    /** Called whenever the app comes back to the front: she may have just changed a system setting. */
    fun refreshSystem() {
        val context = getApplication<Application>()
        val power = context.getSystemService(PowerManager::class.java)
        update {
            it.copy(
                service = ServiceState.read(context),
                notifications = NotificationAvailability.check(context),
                batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
            )
        }
    }

    fun showLaunchReason(reason: LaunchReason?) = update { it.copy(launchReason = reason) }

    /** The trigger opened the app; the guide keeps its place, everything else goes to the home screen. */
    fun showHomeUnlessOnboarding() = update {
        if (it.screen == Screen.ONBOARDING) it else it.copy(screen = Screen.HOME)
    }

    fun dismissLaunchReason() = showLaunchReason(null)

    fun openSettings() = update { it.copy(screen = Screen.SETTINGS, save = SaveState.Idle) }

    fun openHome() = update { it.copy(screen = Screen.HOME, save = SaveState.Idle) }

    fun startOnboarding() = update { it.copy(onboardingStep = Step.CONNECT) }

    fun onboardingNext() = update { state ->
        val next = OnboardingFlow.next(state.onboardingStep)
        if (next == null) state else state.copy(onboardingStep = next)
    }

    fun finishOnboarding() {
        graph.prefs.onboardingDone = true
        update { it.copy(screen = Screen.HOME, save = SaveState.Idle) }
    }

    fun rerunOnboarding() {
        graph.prefs.onboardingDone = false
        update { it.copy(screen = Screen.ONBOARDING, onboardingStep = Step.WELCOME, save = SaveState.Idle) }
    }

    /** Gallery pick: a single image or a count the grouping question does not fit goes straight through. */
    fun photosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (SharedImageSubmitter.asksHowToGroup(uris.size)) {
            update { it.copy(pickedUris = uris) }
        } else {
            submitPicked(uris, together = false)
        }
    }

    fun cancelPick() = update { it.copy(pickedUris = emptyList()) }

    fun submitPicked(uris: List<Uri>, together: Boolean) {
        update { it.copy(pickedUris = emptyList()) }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = SharedImageSubmitter(graph.imageReader, graph.intake)
                .submit(TaskSource.PICKER, uris, together)
            val message = when {
                result.started == 0 -> context.getString(R.string.share_unreadable)
                result.unreadable > 0 -> context.getString(R.string.share_started_partial, result.unreadable)
                else -> context.getString(R.string.share_started)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch { graph.updates.check(manual = true) }
    }

    fun deferUpdate() = graph.updates.defer()

    suspend fun installUpdate(): InstallStart = withContext(Dispatchers.IO) { graph.updates.install() }

    fun retry(id: String) {
        viewModelScope.launch { graph.intake.retry(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { graph.intake.delete(id) }
    }

    /** Checks the connection first and only stores it when Cashier answers as expected. */
    fun testAndSave(baseUrl: String, apiKey: String, force: Boolean = false) {
        val url = BaseUrlValidator.normalize(baseUrl).getOrElse {
            return fail(it.message ?: "服务器地址格式无效")
        }
        val key = ApiKeyValidator.normalize(apiKey).getOrElse {
            return fail(it.message ?: "API 密钥格式无效")
        }
        update { it.copy(save = SaveState.Testing) }
        viewModelScope.launch {
            if (!force) {
                val result = graph.api.testConnection(url, key)
                if (result != ConnectionTest.OK) {
                    update { it.copy(save = failure(result)) }
                    return@launch
                }
            }
            graph.config.save(url, key).fold(
                onSuccess = {
                    graph.intake.connectionFixed()
                    update { it.copy(save = SaveState.Saved) }
                },
                onFailure = { error -> fail(error.message ?: "无法保存设置") },
            )
        }
    }

    fun clearSaveResult() = update { it.copy(save = SaveState.Idle) }

    private fun fail(message: String) = update { it.copy(save = SaveState.Failed(message)) }

    private fun failure(result: ConnectionTest): SaveState.Failed = when (result) {
        ConnectionTest.OK -> SaveState.Failed("")
        ConnectionTest.BAD_KEY -> SaveState.Failed("API 密钥无效，请检查是否复制完整")
        ConnectionTest.NOT_CASHIER -> SaveState.Failed("这个地址不像是 Cashier，请检查服务器地址")
        ConnectionTest.UNREACHABLE -> SaveState.Failed("连不上服务器，请检查网络和地址", canForce = true)
        ConnectionTest.BAD_CERT -> SaveState.Failed("服务器的证书不受信任，请检查地址是否正确")
        ConnectionTest.UNKNOWN_HOST -> SaveState.Failed("找不到这个地址，请检查拼写和网络", canForce = true)
    }
}
