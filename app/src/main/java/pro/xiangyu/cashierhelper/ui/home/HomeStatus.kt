package pro.xiangyu.cashierhelper.ui.home

import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.notify.NotificationAvailability

/** The one thing that stops double press from working, most important first. */
enum class HomeProblem { NOT_CONNECTED, KEY_UNREADABLE, KEY_REJECTED, SERVICE_OFF, SERVICE_NOT_RUNNING }

/** Things that do not stop capturing but are worth fixing. */
enum class Reminder { NOTIFICATIONS_OFF, BATTERY_RESTRICTED }

data class HomeStatus(val problem: HomeProblem?, val reminders: List<Reminder>) {
    val ready: Boolean get() = problem == null
}

object HomeStatusCalculator {
    fun compute(
        config: ConfigState?,
        service: ServiceState,
        keyRejected: Boolean,
        notifications: NotificationAvailability.Status,
        batteryUnrestricted: Boolean,
    ): HomeStatus {
        val problem = when {
            config == null -> null // still loading; do not flash an error
            config is ConfigState.Missing -> HomeProblem.NOT_CONNECTED
            config is ConfigState.KeyUnreadable -> HomeProblem.KEY_UNREADABLE
            keyRejected -> HomeProblem.KEY_REJECTED
            service == ServiceState.OFF -> HomeProblem.SERVICE_OFF
            service == ServiceState.ENABLED_NOT_RUNNING -> HomeProblem.SERVICE_NOT_RUNNING
            else -> null
        }
        val reminders = buildList {
            if (!notifications.isVisible) add(Reminder.NOTIFICATIONS_OFF)
            if (!batteryUnrestricted) add(Reminder.BATTERY_RESTRICTED)
        }
        return HomeStatus(problem, reminders)
    }
}
