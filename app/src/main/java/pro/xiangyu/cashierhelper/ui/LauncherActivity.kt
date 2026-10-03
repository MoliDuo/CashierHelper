package pro.xiangyu.cashierhelper.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pro.xiangyu.cashierhelper.AppGraph
import pro.xiangyu.cashierhelper.CashierHelperApplication
import pro.xiangyu.cashierhelper.capture.CashierAccessibilityService
import pro.xiangyu.cashierhelper.capture.ScreenshotProblem
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.capture.captureScreen

/**
 * Transparent trigger entry point, bound to the side key and the launcher icon.
 *
 * It decides, takes the screenshot, and leaves. Encoding, storing and uploading
 * continue in the application scope after this activity is gone. It lives in
 * its own task (see the manifest) and never hosts a screen, so the next press
 * always starts a capture instead of resurfacing something left behind.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressTransition(open = true)
        val graph = (application as CashierHelperApplication).graph

        lifecycleScope.launch {
            val config = graph.config.current()
            var service = ServiceState.read(this@LauncherActivity)
            if (service == ServiceState.ENABLED_NOT_RUNNING) {
                service = awaitService()
            }
            when (val action = LaunchDecision.decide(graph.trial.armed.value, config, service)) {
                is LaunchAction.OpenApp -> {
                    graph.haptics.error()
                    openApp(action.reason)
                }
                is LaunchAction.Capture -> capture(graph, action.upload)
            }
        }
    }

    /** After a cold start the service can take a moment to connect even though it is switched on. */
    private suspend fun awaitService(): ServiceState {
        withTimeoutOrNull(SERVICE_WAIT_MILLIS) {
            while (!CashierAccessibilityService.isConnected()) delay(SERVICE_POLL_MILLIS)
        }
        return ServiceState.read(this)
    }

    private suspend fun capture(graph: AppGraph, upload: Boolean) {
        val service = CashierAccessibilityService.active()
        val result = service?.captureScreen() ?: Result.failure(IllegalStateException("service not connected"))
        val bitmap = result.getOrNull()
        if (bitmap == null) {
            graph.haptics.error()
            val problem = ScreenshotProblem.describe(result.exceptionOrNull())
            graph.notifier.notice(problem.title, problem.message)
            finishQuietly()
            return
        }

        // The screenshot exists now, so confirm at once; the rest happens without this activity.
        graph.haptics.confirm()
        graph.appScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { graph.encoder.encode(bitmap) }
                if (upload) graph.intake.createFromScreenshot(bytes) else graph.trial.reportSuccess()
            } catch (error: Exception) {
                graph.haptics.error()
                graph.notifier.notice("截图没有保存成功", "请再双击一次侧键")
            } finally {
                bitmap.recycle()
            }
        }
        finishQuietly()
    }

    /**
     * The app opens in its own task. A screen left in this activity's task would be
     * brought back by the next press instead of capturing.
     */
    private fun openApp(reason: LaunchReason) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_LAUNCH_REASON, reason.name),
        )
        finishQuietly()
    }

    private fun finishQuietly() {
        finish()
        suppressTransition(open = false)
    }

    @Suppress("DEPRECATION")
    private fun suppressTransition(open: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                if (open) Activity.OVERRIDE_TRANSITION_OPEN else Activity.OVERRIDE_TRANSITION_CLOSE,
                0,
                0,
            )
        } else {
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        const val SERVICE_WAIT_MILLIS = 3_000L
        const val SERVICE_POLL_MILLIS = 50L
    }
}
