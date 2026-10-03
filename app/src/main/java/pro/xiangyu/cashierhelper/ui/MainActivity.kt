package pro.xiangyu.cashierhelper.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.xiangyu.cashierhelper.BuildConfig
import pro.xiangyu.cashierhelper.ui.home.HomeScreen
import pro.xiangyu.cashierhelper.ui.settings.SettingsScreen
import pro.xiangyu.cashierhelper.ui.theme.CashierTheme

/**
 * The only screen host. It has the app's own task, so the capture trigger's task
 * never ends up holding a screen.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshSystem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readLaunchReason(intent)

        setContent {
            CashierTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                BackHandler(enabled = state.screen == Screen.SETTINGS) { viewModel.openHome() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding(),
                ) {
                    when (state.screen) {
                        Screen.HOME -> HomeScreen(
                            state = state,
                            imageFiles = viewModel.imageFiles,
                            onOpenSettings = viewModel::openSettings,
                            onFixService = { SystemIntents.openAccessibility(context) },
                            onFixNotifications = { requestOrOpenNotifications() },
                            onFixBattery = { SystemIntents.openBattery(context) },
                            onDismissReason = viewModel::dismissLaunchReason,
                            onRetry = viewModel::retry,
                            onDelete = viewModel::delete,
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            state = state,
                            versionName = BuildConfig.VERSION_NAME,
                            onBack = viewModel::openHome,
                            onTestAndSave = viewModel::testAndSave,
                            onEdited = viewModel::clearSaveResult,
                            onFixService = { SystemIntents.openAccessibility(context) },
                            onFixNotifications = { requestOrOpenNotifications() },
                            onFixBattery = { SystemIntents.openBattery(context) },
                            onOpenCashier = { baseUrl -> SystemIntents.openUrl(context, baseUrl) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunchReason(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystem()
    }

    private fun readLaunchReason(intent: Intent?) {
        val reason = intent?.getStringExtra(EXTRA_LAUNCH_REASON)?.let { name ->
            LaunchReason.entries.firstOrNull { it.name == name }
        }
        if (reason != null) {
            viewModel.showLaunchReason(reason)
            viewModel.openHome()
            // The reason is shown once; a rotation must not bring it back.
            intent?.removeExtra(EXTRA_LAUNCH_REASON)
        }
    }

    private fun requestOrOpenNotifications() {
        val status = viewModel.state.value.notifications
        if (!status.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemIntents.openNotifications(this)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_REASON = "launchReason"
    }
}
