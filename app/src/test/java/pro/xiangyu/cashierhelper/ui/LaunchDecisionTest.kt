package pro.xiangyu.cashierhelper.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pro.xiangyu.cashierhelper.capture.ServiceState
import pro.xiangyu.cashierhelper.config.AppConfig
import pro.xiangyu.cashierhelper.config.ConfigState

class LaunchDecisionTest {
    private val ready = ConfigState.Ready(AppConfig("https://cashier.example", "key"))

    private fun decide(
        config: ConfigState = ready,
        service: ServiceState = ServiceState.RUNNING,
        trial: Boolean = false,
    ) = LaunchDecision.decide(trial, config, service)

    @Test
    fun `everything in place captures and uploads`() {
        assertEquals(LaunchAction.Capture(upload = true), decide())
    }

    @Test
    fun `no connection opens the app with the reason`() {
        assertEquals(LaunchAction.OpenApp(LaunchReason.NOT_CONFIGURED), decide(config = ConfigState.Missing()))
        assertEquals(
            LaunchAction.OpenApp(LaunchReason.KEY_UNREADABLE),
            decide(config = ConfigState.KeyUnreadable("https://cashier.example")),
        )
    }

    @Test
    fun `a service that is off or not running opens the app with its own reason`() {
        assertEquals(LaunchAction.OpenApp(LaunchReason.SERVICE_OFF), decide(service = ServiceState.OFF))
        assertEquals(
            LaunchAction.OpenApp(LaunchReason.SERVICE_NOT_RUNNING),
            decide(service = ServiceState.ENABLED_NOT_RUNNING),
        )
    }

    @Test
    fun `a missing connection is reported before a missing service`() {
        assertEquals(
            LaunchAction.OpenApp(LaunchReason.NOT_CONFIGURED),
            decide(config = ConfigState.Missing(), service = ServiceState.OFF),
        )
    }

    @Test
    fun `the setup test captures and discards, even before a connection exists`() {
        assertEquals(LaunchAction.Capture(upload = false), decide(trial = true))
        assertEquals(LaunchAction.Capture(upload = false), decide(config = ConfigState.Missing(), trial = true))
    }

    @Test
    fun `the setup test still needs the service`() {
        assertEquals(LaunchAction.OpenApp(LaunchReason.SERVICE_OFF), decide(service = ServiceState.OFF, trial = true))
    }
}
