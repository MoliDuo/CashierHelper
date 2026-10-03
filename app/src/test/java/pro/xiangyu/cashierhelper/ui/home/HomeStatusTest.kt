package pro.xiangyu.cashierhelper.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.config.ConfigState
import pro.xiangyu.cashierhelper.notify.NotificationAvailability

class HomeStatusTest {
    private val ready = ConfigState.Ready(AppConfig("https://cashier.example", "key"))
    private val visible = NotificationAvailability.Status(permissionGranted = true, appEnabled = true, channelEnabled = true)

    private fun compute(
        config: ConfigState? = ready,
        service: ServiceState = ServiceState.RUNNING,
        keyRejected: Boolean = false,
        notifications: NotificationAvailability.Status = visible,
        battery: Boolean = true,
    ) = HomeStatusCalculator.compute(config, service, keyRejected, notifications, battery)

    @Test
    fun `all good is ready with no reminders`() {
        val status = compute()

        assertTrue(status.ready)
        assertTrue(status.reminders.isEmpty())
    }

    @Test
    fun `the most important problem wins`() {
        assertEquals(HomeProblem.NOT_CONNECTED, compute(config = ConfigState.Missing(), service = ServiceState.OFF).problem)
        assertEquals(
            HomeProblem.KEY_UNREADABLE,
            compute(config = ConfigState.KeyUnreadable(""), keyRejected = true, service = ServiceState.OFF).problem,
        )
        assertEquals(HomeProblem.KEY_REJECTED, compute(keyRejected = true, service = ServiceState.OFF).problem)
        assertEquals(HomeProblem.SERVICE_OFF, compute(service = ServiceState.OFF).problem)
        assertEquals(HomeProblem.SERVICE_NOT_RUNNING, compute(service = ServiceState.ENABLED_NOT_RUNNING).problem)
    }

    @Test
    fun `nothing is reported while the configuration is still loading`() {
        assertNull(compute(config = null).problem)
    }

    @Test
    fun `soft reminders do not block`() {
        val status = compute(notifications = visible.copy(appEnabled = false), battery = false)

        assertTrue(status.ready)
        assertEquals(listOf(Reminder.NOTIFICATIONS_OFF, Reminder.BATTERY_RESTRICTED), status.reminders)
    }
}
