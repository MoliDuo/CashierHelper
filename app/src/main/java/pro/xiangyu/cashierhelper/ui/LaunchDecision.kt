package pro.xiangyu.cashierhelper.ui

import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.ConfigState

/** Why the trigger opened the app instead of taking a screenshot. Shown as a banner on the home screen. */
enum class LaunchReason { NOT_CONFIGURED, KEY_UNREADABLE, SERVICE_OFF, SERVICE_NOT_RUNNING }

sealed interface LaunchAction {
    /** Take the screenshot; with [upload] false it is discarded, which is the setup guide's side key test. */
    data class Capture(val upload: Boolean) : LaunchAction

    data class OpenApp(val reason: LaunchReason) : LaunchAction
}

/** The trigger's decision as a pure function of what is known when it fires. */
object LaunchDecision {
    fun decide(trialPending: Boolean, config: ConfigState, service: ServiceState): LaunchAction {
        // The guide's last step wants to know the side key works, whatever else is not set up yet.
        if (trialPending) {
            return if (service == ServiceState.RUNNING) {
                LaunchAction.Capture(upload = false)
            } else {
                LaunchAction.OpenApp(serviceReason(service))
            }
        }
        return when {
            config is ConfigState.Missing -> LaunchAction.OpenApp(LaunchReason.NOT_CONFIGURED)
            config is ConfigState.KeyUnreadable -> LaunchAction.OpenApp(LaunchReason.KEY_UNREADABLE)
            service != ServiceState.RUNNING -> LaunchAction.OpenApp(serviceReason(service))
            else -> LaunchAction.Capture(upload = true)
        }
    }

    private fun serviceReason(service: ServiceState) =
        if (service == ServiceState.OFF) LaunchReason.SERVICE_OFF else LaunchReason.SERVICE_NOT_RUNNING
}
