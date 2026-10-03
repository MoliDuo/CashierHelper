package pro.xiangyu.cashierhelper.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private val nothing = Signals(
        connected = false,
        serviceRunning = false,
        notificationsVisible = false,
        batteryUnrestricted = false,
        sideKeyWorked = false,
    )

    @Test
    fun `the welcome page waits for the start button`() {
        assertEquals(Step.WELCOME, OnboardingFlow.settle(Step.WELCOME, nothing.copy(connected = true, serviceRunning = true)))
    }

    @Test
    fun `an incomplete step stays put`() {
        assertEquals(Step.CONNECT, OnboardingFlow.settle(Step.CONNECT, nothing))
        assertEquals(Step.SERVICE, OnboardingFlow.settle(Step.SERVICE, nothing.copy(connected = true)))
    }

    @Test
    fun `finished steps move on by themselves`() {
        assertEquals(Step.SERVICE, OnboardingFlow.settle(Step.CONNECT, nothing.copy(connected = true)))
        assertEquals(
            Step.BATTERY,
            OnboardingFlow.settle(Step.CONNECT, nothing.copy(connected = true, serviceRunning = true, notificationsVisible = true)),
        )
    }

    @Test
    fun `the last step never advances on its own`() {
        val all = Signals(true, true, true, true, true)

        assertEquals(Step.SIDE_KEY, OnboardingFlow.settle(Step.CONNECT, all))
        assertEquals(Step.SIDE_KEY, OnboardingFlow.settle(Step.SIDE_KEY, all))
    }

    @Test
    fun `a skipped optional step is not undone by settling`() {
        // She skipped the battery step: the guide is already on the side key step and stays there.
        assertEquals(Step.SIDE_KEY, OnboardingFlow.settle(Step.SIDE_KEY, nothing.copy(connected = true)))
    }

    @Test
    fun `steps are numbered without the welcome page`() {
        assertNull(OnboardingFlow.position(Step.WELCOME))
        assertEquals(1, OnboardingFlow.position(Step.CONNECT))
        assertEquals(5, OnboardingFlow.position(Step.SIDE_KEY))
        assertEquals(5, OnboardingFlow.numbered.size)
    }

    @Test
    fun `next walks the steps in order and ends after the last`() {
        assertEquals(Step.CONNECT, OnboardingFlow.next(Step.WELCOME))
        assertEquals(Step.SIDE_KEY, OnboardingFlow.next(Step.BATTERY))
        assertNull(OnboardingFlow.next(Step.SIDE_KEY))
    }

    @Test
    fun `the restricted settings help is shown for sideloaded installs on Android 13 and up`() {
        assertTrue(RestrictedSettings.shouldExplain(installer = null, sdk = 34))
        assertTrue(RestrictedSettings.shouldExplain(installer = "com.google.android.packageinstaller", sdk = 33))
        assertFalse(RestrictedSettings.shouldExplain(installer = "com.android.vending", sdk = 34))
        assertFalse(RestrictedSettings.shouldExplain(installer = null, sdk = 32))
    }
}
