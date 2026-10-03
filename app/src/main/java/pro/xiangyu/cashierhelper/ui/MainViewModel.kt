package pro.xiangyu.cashierhelper.ui

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pro.xiangyu.cashierhelper.CashierHelperApplication
import pro.xiangyu.cashierhelper.api.ConnectionTest
import pro.xiangyu.cashierhelper.capture.CashierAccessibilityService
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.ApiKeyValidator
import pro.xiangyu.cashierhelper.config.BaseUrlValidator
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.notify.NotificationAvailability
import pro.xiangyu.cashierhelper.tasks.TaskProblem
import pro.xiangyu.cashierhelper.tasks.TaskRecord
import pro.xiangyu.cashierhelper.tasks.TaskState
import pro.xiangyu.cashierhelper.ui.home.HomeStatus
import pro.xiangyu.cashierhelper.ui.home.HomeStatusCalculator

enum class Screen { HOME, SETTINGS }

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
) {
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
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            graph.config.current()
            graph.config.state.collect { config -> mutableState.update { it.copy(config = config) } }
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
        refreshSystem()
    }

    val imageFiles get() = graph.images

    /** Called whenever the app comes back to the front: she may have just changed a system setting. */
    fun refreshSystem() {
        val context = getApplication<Application>()
        val power = context.getSystemService(PowerManager::class.java)
        mutableState.update {
            it.copy(
                service = ServiceState.read(context),
                notifications = NotificationAvailability.check(context),
                batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
            )
        }
    }

    fun showLaunchReason(reason: LaunchReason?) = mutableState.update { it.copy(launchReason = reason) }

    fun dismissLaunchReason() = showLaunchReason(null)

    fun openSettings() = mutableState.update { it.copy(screen = Screen.SETTINGS, save = SaveState.Idle) }

    fun openHome() = mutableState.update { it.copy(screen = Screen.HOME, save = SaveState.Idle) }

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
        mutableState.update { it.copy(save = SaveState.Testing) }
        viewModelScope.launch {
            if (!force) {
                val result = graph.api.testConnection(url, key)
                if (result != ConnectionTest.OK) {
                    mutableState.update { it.copy(save = failure(result)) }
                    return@launch
                }
            }
            graph.config.save(url, key).fold(
                onSuccess = {
                    graph.intake.connectionFixed()
                    mutableState.update { it.copy(save = SaveState.Saved) }
                },
                onFailure = { error -> fail(error.message ?: "无法保存设置") },
            )
        }
    }

    fun clearSaveResult() = mutableState.update { it.copy(save = SaveState.Idle) }

    private fun fail(message: String) = mutableState.update { it.copy(save = SaveState.Failed(message)) }

    private fun failure(result: ConnectionTest): SaveState.Failed = when (result) {
        ConnectionTest.OK -> SaveState.Failed("")
        ConnectionTest.BAD_KEY -> SaveState.Failed("API 密钥无效，请检查是否复制完整")
        ConnectionTest.NOT_CASHIER -> SaveState.Failed("这个地址不像是 Cashier，请检查服务器地址")
        ConnectionTest.UNREACHABLE -> SaveState.Failed("连不上服务器，请检查网络和地址", canForce = true)
        ConnectionTest.BAD_CERT -> SaveState.Failed("服务器的证书不受信任，请检查地址是否正确")
        ConnectionTest.UNKNOWN_HOST -> SaveState.Failed("找不到这个地址，请检查拼写和网络", canForce = true)
    }
}
